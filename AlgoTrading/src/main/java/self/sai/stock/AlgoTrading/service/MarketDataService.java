package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.AngelCandleResponse;
import self.sai.stock.AlgoTrading.dto.CandleBar;
import self.sai.stock.AlgoTrading.entity.Instrument;
import self.sai.stock.AlgoTrading.repository.InstrumentRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches 75 days of 10-min OHLCV candle data from AngelOne for every
 * instrument whose type is {@code "10MinWatchlist"} in the {@code instruments} table,
 * and bulk-inserts the bars into the {@code candles} table.
 *
 * <p>One API call per instrument covers the full 75-day window (~60 trading sessions).
 * Rate limit: ~3 API calls/second; 100 ms sleep + ~200 ms network ≈ 1 call per 300 ms.
 * 20 instruments → ~6 seconds total.
 */
@Service
public class MarketDataService {
    public InstrumentRepository getInstrumentRepository() {
        return instrumentRepository;
    }

    /**
     * Fetches the last N session candles for a given instrument token.
     * This is a helper for the scheduler; you may want to optimize as needed.
     */
    public Map<String, List<CandleBar>> fetchLastNSessionCandles(String clientcode, boolean onlyToday) {
    	var session = sessionStore.get(clientcode);
        if (session == null || session.getData() == null) {
            throw new IllegalStateException(
                    "No active AngelOne session for clientcode: " + clientcode);
        }
        String jwtToken = session.getData().getJwtToken();

        List<Instrument> instruments = instrumentRepository.findByType("10MinWatchlist");
        if (instruments.isEmpty()) {
            log.warn("No 10MinWatchlist instruments found — run POST /api/instruments/refresh first");
            return null;
        }
        log.info("Fetching {}-day 10-min candles for {} instruments",
                TOTAL_DAYS, instruments.size());

        LocalDateTime toDateTime = LocalDateTime.now(IST).minusMinutes(2);
        
        LocalDateTime fromDateTime = null;
        
        if(onlyToday) {
        	log.info("Fetching today candles....");
        	fromDateTime = toDateTime
                    .withHour(9).withMinute(15).withSecond(0).withNano(0);
        } else {
        	fromDateTime = toDateTime
                    .minusDays(TOTAL_DAYS)
                    .withHour(9).withMinute(15).withSecond(0).withNano(0);
        }

        String fromStr = fromDateTime.format(FMT);
        String toStr   = toDateTime.format(FMT);
        log.info("Date range: {} → {}", fromStr, toStr);
        
        Map<String, List<CandleBar>> candleMap = new ConcurrentHashMap();
        
        for (Instrument inst : instruments) {
        	try {
				List<CandleBar> bars =
				        callCandleApi(jwtToken, inst.getToken(), inst.getExchange(), fromStr, toStr);
				candleMap.put(inst.getToken(), bars);
				
				Thread.sleep(RATE_DELAY_MS);
			} catch (Exception e) {
				e.printStackTrace();
			}
        }
        return candleMap;
    }

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final String CANDLE_URL =
            "https://apiconnect.angelone.in/rest/secure/angelbroking/historical/v1/getCandleData";

    /** AngelOne interval code for 10-min candles. */
    private static final String INTERVAL = "TEN_MINUTE";

    /** Total calendar-day window — covers ~60 trading sessions (weekends + holidays included). */
    private static final int TOTAL_DAYS = 150;

    /** Delay between consecutive API calls (ms) — call latency (~200ms) keeps us under 3/s. */
    private static final long RATE_DELAY_MS = 500;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId            IST = ZoneId.of("Asia/Kolkata");

    @Value("${angelone.api.key:}")
    private String apiKey;

    private final SessionStoreService  sessionStore;
    private final InstrumentRepository instrumentRepository;
    private final JdbcTemplate         jdbcTemplate;
    private final ObjectMapper         objectMapper;
    private final HttpClient           http;

    public MarketDataService(SessionStoreService sessionStore,
                             InstrumentRepository instrumentRepository,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper) {
        this.sessionStore         = sessionStore;
        this.instrumentRepository = instrumentRepository;
        this.jdbcTemplate         = jdbcTemplate;
        this.objectMapper         = objectMapper;
        this.http                 = HttpClient.newHttpClient();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches 75 days of 10-min candles for all {@code 10MinWatchlist} instruments
     * and saves them to the {@code candles} table (truncates first).
     *
     * @param clientcode AngelOne clientcode whose active session will be used
     * @return total number of candle rows inserted
     */
    public int fetchCandles(String clientcode) {
        var session = sessionStore.get(clientcode);
        if (session == null || session.getData() == null) {
            throw new IllegalStateException(
                    "No active AngelOne session for clientcode: " + clientcode);
        }
        String jwtToken = session.getData().getJwtToken();

        List<Instrument> instruments = instrumentRepository.findByType("10MinWatchlist");
        if (instruments.isEmpty()) {
            log.warn("No 10MinWatchlist instruments found — run POST /api/instruments/refresh first");
            return 0;
        }
        log.info("Fetching {}-day 10-min candles for {} instruments",
                TOTAL_DAYS, instruments.size());

        LocalDateTime toDateTime = LocalDateTime.now(IST).minusMinutes(2);
        LocalDateTime fromDateTime = toDateTime
                .minusDays(TOTAL_DAYS)
                .withHour(9).withMinute(15).withSecond(0).withNano(0);

        String fromStr = fromDateTime.format(FMT);
        String toStr   = toDateTime.format(FMT);
        log.info("Date range: {} → {}", fromStr, toStr);

        // ── Truncate before fresh load ────────────────────────────────────────
        jdbcTemplate.execute("TRUNCATE TABLE candles");
        log.info("Candles table truncated");

        // ── One API call per instrument ───────────────────────────────────────
        int totalInserted = 0;
        for (Instrument inst : instruments) {
            try {
                List<CandleBar> bars =
                        callCandleApi(jwtToken, inst.getToken(), inst.getExchange(), fromStr, toStr);
                int inserted = saveBars(inst.getToken(), inst.getName(), bars);
                log.info("  {} → {} candles inserted", inst.getName(), inserted);
                totalInserted += inserted;
                Thread.sleep(RATE_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during candle fetch", e);
            } catch (Exception e) {
                log.warn("  Failed for {}: {}", inst.getName(), e.getMessage());
            }
        }

        log.info("Market data fetch complete — {} total candle rows inserted", totalInserted);
        return totalInserted;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Calls AngelOne getCandleData using the default 10-min interval. */
    private List<CandleBar> callCandleApi(String jwtToken, String token,
                                          String exchange, String fromdate,
                                          String todate) throws Exception {
        return callCandleApi(jwtToken, token, exchange, fromdate, todate, INTERVAL);
    }

    /** Calls AngelOne getCandleData with the specified interval and returns parsed bars. */
    private List<CandleBar> callCandleApi(String jwtToken, String token,
                                          String exchange, String fromdate,
                                          String todate, String interval) throws Exception {
        Map<String, String> bodyMap = new LinkedHashMap<>();
        bodyMap.put("exchange",    exchange);
        bodyMap.put("symboltoken", token);
        bodyMap.put("interval",    interval);
        bodyMap.put("fromdate",    fromdate);
        bodyMap.put("todate",      todate);

        String bodyJson = objectMapper.writeValueAsString(bodyMap);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CANDLE_URL))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .header("Content-Type",    "application/json")
                .header("Accept",          "application/json")
                .header("Authorization",   "Bearer " + jwtToken)
                .header("X-UserType",      "USER")
                .header("X-SourceID",      "WEB")
                .header("X-ClientLocalIP",  "127.0.0.1")
                .header("X-ClientPublicIP", "127.0.0.1")
                .header("X-MACAddress",    "00-00-00-00-00-00")
                .header("X-PrivateKey",    apiKey)
                .build();

        HttpResponse<String> response =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        AngelCandleResponse resp =
                objectMapper.readValue(response.body(), AngelCandleResponse.class);
        if (!resp.isStatus()) {
            log.warn("CandleData API error token={} {}-{}: {} {}",
                    token, fromdate, todate, resp.getErrorcode(), resp.getMessage());
            return Collections.emptyList();
        }
        return resp.getCandles();
    }

    /** Batch-inserts candle bars for one instrument into the candles table. */
    private int saveBars(String token, String name, List<CandleBar> bars) {
        if (bars.isEmpty()) return 0;

        List<Object[]> rows = new ArrayList<>(bars.size());
        for (CandleBar bar : bars) {
            rows.add(new Object[]{
                    token,
                    name,
                    bar.getTimestamp().format(FMT),
                    bar.getOpen(),
                    bar.getHigh(),
                    bar.getLow(),
                    bar.getClose(),
                    bar.getClose(),   // closeAlt = close
                    bar.getOpen(),    // openAlt  = open
                    "10Min"
            });
        }

        final int CHUNK = 500;
        int count = 0;
        for (int i = 0; i < rows.size(); i += CHUNK) {
            List<Object[]> batch = rows.subList(i, Math.min(i + CHUNK, rows.size()));
            jdbcTemplate.batchUpdate(
                    "INSERT INTO candles (token, name, date, open, high, low, close," +
                    " close_alt, open_alt, type) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    batch);
            count += batch.size();
        }
        return count;
    }

    // ── Daily (1-year) candles — not persisted ────────────────────────────────

    /**
     * Fetches 1 year of daily OHLCV candles from AngelOne for the given token.
     * Data is returned directly and never saved to the database.
     *
     * @param clientcode AngelOne clientcode (session must already be active)
     * @param token      AngelOne symbol token
     * @return list of {@link CandleBar} ordered oldest → newest
     */
    public List<CandleBar> fetchDailyCandles(String clientcode, String token) {
        var session = sessionStore.get(clientcode);
        if (session == null || session.getData() == null) {
            throw new IllegalStateException(
                    "No active AngelOne session for clientcode: " + clientcode);
        }
        String jwtToken = session.getData().getJwtToken();

        Instrument inst = instrumentRepository.findById(token)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Instrument not found for token: " + token));

        LocalDateTime toDateTime = LocalDateTime.now(IST)
                .withHour(15).withMinute(30).withSecond(0).withNano(0);
        if (LocalDateTime.now(IST).toLocalTime().isBefore(toDateTime.toLocalTime())) {
            toDateTime = toDateTime.minusDays(1);
        }
        LocalDateTime fromDateTime = toDateTime.minusDays(800)
                .withHour(9).withMinute(15).withSecond(0).withNano(0);

        String fromStr = fromDateTime.format(FMT);
        String toStr   = toDateTime.format(FMT);
        log.info("Daily candles: {} token={} range {} → {}", inst.getName(), token, fromStr, toStr);

        try {
            return callCandleApi(jwtToken, token, inst.getExchange(), fromStr, toStr, "ONE_DAY");
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch daily candles for " + token + ": " + e.getMessage(), e);
        }
    }
    
    public Map<String, List<CandleBar>> fetchDailyCandles(String clientcode){
    	Map<String, List<CandleBar>> candleMap = new ConcurrentHashMap<>();
    	List<Instrument> instruments = instrumentRepository.findByType("DailyWatchlist");
        if (instruments.isEmpty()) {
            log.warn("No 10MinWatchlist instruments found — run POST /api/instruments/refresh first");
            return candleMap;
        }
        instruments.forEach(inst -> {
        	List<CandleBar> candles = fetchDailyCandles(clientcode, inst.getToken());
        	if(candles != null && !candles.isEmpty()) {
        		candleMap.put(inst.getToken(), candles);
        	}
        });
    	return candleMap;
    }

    /**
     * Fetches 5-minute OHLCV candles from AngelOne for all {@code Index} type instruments.
     * Used by the intraday index option scheduler.
     *
     * @param clientcode AngelOne clientcode (session must already be active)
     * @param onlyToday  if true, fetches only today's session candles;
     *                   if false, fetches full historical window (TOTAL_DAYS)
     * @return token → candle list map
     */
    public Map<String, List<CandleBar>> fetchIndexCandles(String clientcode, boolean onlyToday) {
        var session = sessionStore.get(clientcode);
        if (session == null || session.getData() == null) {
            throw new IllegalStateException(
                    "No active AngelOne session for clientcode: " + clientcode);
        }
        String jwtToken = session.getData().getJwtToken();

        List<Instrument> instruments = instrumentRepository.findByType("Index");
        if (instruments.isEmpty()) {
            log.warn("No Index instruments found — run POST /api/instruments/refresh first");
            return new ConcurrentHashMap<>();
        }
        log.info("Fetching 5-min {} candles for {} index instrument(s)",
                onlyToday ? "today" : TOTAL_DAYS + "-day", instruments.size());

        LocalDateTime toDateTime = LocalDateTime.now(IST).minusMinutes(2);
        LocalDateTime fromDateTime = onlyToday
                ? toDateTime.withHour(9).withMinute(15).withSecond(0).withNano(0)
                : toDateTime.minusDays(TOTAL_DAYS).withHour(9).withMinute(15).withSecond(0).withNano(0);

        String fromStr = fromDateTime.format(FMT);
        String toStr   = toDateTime.format(FMT);
        log.info("Index candle range: {} → {}", fromStr, toStr);

        Map<String, List<CandleBar>> candleMap = new ConcurrentHashMap<>();
        for (Instrument inst : instruments) {
            try {
                List<CandleBar> bars = callCandleApi(
                        jwtToken, inst.getToken(), inst.getExchange(), fromStr, toStr, "FIVE_MINUTE");
                candleMap.put(inst.getToken(), bars);
                log.info("  Index {} → {} candles fetched", inst.getName(), bars.size());
                Thread.sleep(RATE_DELAY_MS);
            } catch (Exception e) {
                log.warn("  Failed index candles for {}: {}", inst.getName(), e.getMessage());
            }
        }
        return candleMap;
    }
}
