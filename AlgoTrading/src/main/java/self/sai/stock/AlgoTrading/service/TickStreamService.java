package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.glassfish.tyrus.client.ClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.LiveTick;
import self.sai.stock.AlgoTrading.entity.Instrument;
import self.sai.stock.AlgoTrading.repository.InstrumentRepository;
import self.sai.stock.AlgoTrading.repository.TradeRepository;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Connects to AngelOne SmartStream WebSocket (Quote mode, binary protocol).
 * Subscribes to all instruments from the {@code instruments} table and logs each tick.
 *
 * <p>Binary layout (Little Endian, ≥123 bytes):
 * <pre>
 *   byte[0]       : subscription mode (1=LTP, 2=Quote, 3=SnapQuote)
 *   byte[1]       : exchange type
 *   byte[2..26]   : symbol token (25 bytes, null-padded UTF-8)
 *   byte[27..34]  : sequence number (int64)
 *   byte[35..42]  : exchange timestamp (int64, epoch ms)
 *   byte[43..50]  : LTP (int64, paise or ×100,000)
 *   byte[51..58]  : last traded qty (int64)
 *   byte[59..66]  : avg traded price (int64)
 *   byte[67..74]  : volume (int64)
 *   byte[91..98]  : open price (int64)
 *   byte[99..106] : high price (int64)
 *   byte[107..114]: low price (int64)
 *   byte[115..122]: close/prev-day price (int64)
 * </pre>
 */
@Service
public class TickStreamService {

    private static final Logger log = LoggerFactory.getLogger(TickStreamService.class);

    private static final String WS_URL                = "wss://smartapisocket.angelone.in/smart-stream";
    private static final long   HEARTBEAT_INTERVAL_SEC = 28;

    @Value("${angelone.api.key:}")
    private String apiKey;

    private final SessionStoreService    sessionStore;
    private final InstrumentRepository   instrumentRepository;
    private final TradeRepository        tradeRepository;
    private final TradeMonitorService    tradeMonitor;
    private final ObjectMapper           objectMapper;

    private final AtomicBoolean streaming         = new AtomicBoolean(false);
    private Session                wsSession;
    private ScheduledExecutorService heartbeatScheduler;

    /** token → instrument name — built when stream starts */
    private Map<String, String> tokenNameMap = Collections.emptyMap();

    public TickStreamService(SessionStoreService sessionStore,
                             InstrumentRepository instrumentRepository,
                             TradeRepository tradeRepository,
                             TradeMonitorService tradeMonitor,
                             ObjectMapper objectMapper) {
        this.sessionStore         = sessionStore;
        this.instrumentRepository = instrumentRepository;
        this.tradeRepository      = tradeRepository;
        this.tradeMonitor         = tradeMonitor;
        this.objectMapper         = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isStreaming() { return streaming.get(); }

    /**
     * Starts the SmartStream for the given AngelOne clientcode.
     * Fetches all instruments from the DB and subscribes them.
     */
    public synchronized void start(String clientcode) {
        if (streaming.get()) {
            log.info("TickStream already running");
            return;
        }

        var session = sessionStore.get(clientcode);
        if (session == null || session.getData() == null) {
            throw new IllegalStateException("No active AngelOne session for clientcode: " + clientcode);
        }

        String jwtToken  = session.getData().getJwtToken();
        String feedToken = session.getData().getFeedToken();

        // 2. Distinct (token, name) pairs from active trades only
        List<String> activeStatuses = List.of("WATCHING", "BUY PLACED", "SELL PLACED", "OPEN");
        List<Object[]> tradeTokens = tradeRepository.findDistinctTokenAndNameByStatusIn(activeStatuses);

        // 3. Build unique map keyed by token
        Map<String, String> merged = new LinkedHashMap<>();
        for (Object[] row : tradeTokens) {
            merged.putIfAbsent((String) row[0], (String) row[1]);
        }

        if (merged.isEmpty()) {
            log.warn("No active trades to subscribe (statuses: {})", activeStatuses);
            return;
        }

        // Build token → name map for tick parsing
        tokenNameMap = Collections.unmodifiableMap(merged);

        // Build a minimal Instrument list for the subscription payload
        List<Instrument> instruments = new ArrayList<>();
        for (Object[] row : tradeTokens) {
            Instrument stub = new Instrument();
            stub.setToken((String) row[0]);
            stub.setName((String) row[1]);
            stub.setExchange("NSE");
            instruments.add(stub);
        }
        
        log.info("Subscribing {} instruments to SmartStream", instruments.size());

        try {
            String url = WS_URL
                    + "?clientCode=" + encode(clientcode)
                    + "&feedToken="  + encode(feedToken)
                    + "&apiKey="     + encode(apiKey);

            ClientManager client = ClientManager.createClient();
            TickStreamEndpoint endpoint = new TickStreamEndpoint(jwtToken, feedToken, clientcode, instruments);

            wsSession = client.connectToServer(endpoint, URI.create(url));
            streaming.set(true);
            tradeMonitor.onStreamStart();

            heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "tickstream-heartbeat");
                t.setDaemon(true);
                return t;
            });
            heartbeatScheduler.scheduleAtFixedRate(
                    this::sendPing,
                    HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

            log.info("TickStream connected for clientcode={}, instruments={}", clientcode, instruments.size());

        } catch (Exception e) {
            streaming.set(false);
            throw new RuntimeException("TickStream connect failed: " + e.getMessage(), e);
        }
    }

    /** Stops the WebSocket connection and heartbeat. */
    public synchronized void stop() {
        if (!streaming.get()) return;
        streaming.set(false);
        tradeMonitor.onStreamStop();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.shutdownNow();
            heartbeatScheduler = null;
        }
        if (wsSession != null && wsSession.isOpen()) {
            try { wsSession.close(); } catch (Exception ignored) {}
            wsSession = null;
        }
        log.info("TickStream stopped");
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private void sendPing() {
        if (wsSession != null && wsSession.isOpen()) {
            try {
                wsSession.getBasicRemote().sendText("ping");
            } catch (Exception e) {
                log.warn("Heartbeat ping failed: {}", e.getMessage());
            }
        }
    }

    // ── Inner WebSocket endpoint ───────────────────────────────────────────────

    @ClientEndpoint
    public class TickStreamEndpoint {

        private final List<Instrument> instruments;

        TickStreamEndpoint(String jwtToken, String feedToken,
                           String clientcode, List<Instrument> instruments) {
            this.instruments = instruments;
        }

        @OnOpen
        public void onOpen(Session session) {
            log.info("SmartStream WebSocket opened — subscribing {} instruments", instruments.size());
            sendSubscription(session);
        }

        @OnMessage
        public void onBinary(Session session, byte[] message) {
            if (message == null || message.length < 123) return;
            try {
                LiveTick tick = parseBinary(message);
                log.debug("TICK: {}", tick);
                tradeMonitor.onTick(tick);
            } catch (Exception e) {
                log.warn("Error processing tick ({} bytes): {}", message.length, e.getMessage());
            }
        }

        @OnMessage
        public void onText(Session session, String text) {
            if ("pong".equalsIgnoreCase(text.trim())) {
                log.debug("Received pong");
            } else {
                log.debug("SmartStream text message: {}", text);
            }
        }

        @OnClose
        public void onClose(Session session, CloseReason reason) {
            log.warn("SmartStream closed: {}", reason);
            streaming.set(false);
            tradeMonitor.onStreamStop();
        }

        @OnError
        public void onError(Session session, Throwable t) {
            log.error("SmartStream error: {}", t.getMessage(), t);
        }

        /** Sends a subscribe JSON for all instruments, grouped by exchange type. */
        private void sendSubscription(Session session) {
            try {
                // Group tokens by exchange type code
                Map<Integer, List<String>> byExchange = new LinkedHashMap<>();
                for (Instrument inst : instruments) {
                    int exType = exchangeType(inst.getExchange());
                    byExchange.computeIfAbsent(exType, k -> new ArrayList<>())
                              .add(inst.getToken());
                }

                List<Map<String, Object>> tokenListJson = new ArrayList<>();
                byExchange.forEach((exType, tokens) -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("exchangeType", exType);
                    entry.put("tokens", tokens);
                    tokenListJson.add(entry);
                });

                Map<String, Object> params = new LinkedHashMap<>();
                params.put("mode", 2);               // Quote mode
                params.put("tokenList", tokenListJson);

                Map<String, Object> request = new LinkedHashMap<>();
                request.put("action", 1);            // subscribe
                request.put("params", params);

                String json = objectMapper.writeValueAsString(request);
                session.getBasicRemote().sendText(json);
                log.info("Subscription sent: {} exchange groups", tokenListJson.size());
            } catch (Exception e) {
                log.error("Subscription failed: {}", e.getMessage(), e);
            }
        }
    }

    // ── Binary parser ─────────────────────────────────────────────────────────

    private LiveTick parseBinary(byte[] msg) {
        ByteBuffer buf = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN);

        // bytes[2..26] = symbol token (25 bytes, null-padded)
        byte[] tokenBytes = new byte[25];
        buf.position(2);
        buf.get(tokenBytes);
        String symboltoken = new String(tokenBytes, java.nio.charset.StandardCharsets.UTF_8)
                                 .replace("\0", "").trim();

        // bytes[35..42] = exchange timestamp (int64, epoch ms)
        long exchangeTimestamp = buf.getLong(35);

        // bytes[43..50] = LTP (int64, paise or ×100,000)
        long ltpRaw = buf.getLong(43);
        double scale = (ltpRaw > 10_000_000L) ? 100_000.0 : 100.0;
        double ltp   = ltpRaw / scale;

        // bytes[51..58] = last traded qty (int64)
        long lastTradedQty = buf.getLong(51);

        // bytes[67..74] = volume (int64)
        long volume = buf.getLong(67);

        // bytes[91..122] = open/high/low/close (int64 each)
        double open  = buf.getLong(91)  / scale;
        double high  = buf.getLong(99)  / scale;
        double low   = buf.getLong(107) / scale;
        double close = buf.getLong(115) / scale;

        double change = (close > 0) ? ((ltp - close) / close) * 100.0 : 0.0;

        String name = tokenNameMap.getOrDefault(symboltoken, symboltoken);

        LiveTick tick = new LiveTick();
        tick.setSymboltoken(symboltoken);
        tick.setName(name);
        tick.setExchangeTimestamp(exchangeTimestamp);
        tick.setLtp(ltp);
        tick.setLastTradedQty(lastTradedQty);
        tick.setVolume(volume);
        tick.setOpen(open);
        tick.setHigh(high);
        tick.setLow(low);
        tick.setClose(close);
        tick.setChangePercent(change);
        return tick;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Maps exchange name to AngelOne exchange type code. */
    private int exchangeType(String exchange) {
        if (exchange == null) return 1;
        return switch (exchange.toUpperCase()) {
            case "NSE"  -> 1;
            case "NFO"  -> 2;
            case "BSE"  -> 3;
            case "BFO"  -> 4;
            case "MCX"  -> 5;
            default     -> 1;
        };
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }
}
