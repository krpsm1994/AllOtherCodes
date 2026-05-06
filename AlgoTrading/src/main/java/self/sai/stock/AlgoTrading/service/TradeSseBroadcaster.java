package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import self.sai.stock.AlgoTrading.entity.Trade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SSE broadcaster for AlgoTrading trade events.
 *
 * <p>Events:
 * <ul>
 *   <li>{@code trade_snapshot} — one event per trade, sent immediately on SSE connect</li>
 *   <li>{@code new_trade}      — new trade created</li>
 *   <li>{@code trade_update}   — existing trade status/fields changed</li>
 * </ul>
 *
 * <p>Heartbeat comment is sent every 30 s to keep the connection alive through proxies.
 */
@Service
public class TradeSseBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(TradeSseBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public TradeSseBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trade-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * Registers a new SSE client. Immediately flushes {@code currentTrades} as individual
     * {@code trade_snapshot} events so the UI has the full picture, then keeps the
     * emitter open for future broadcasts.
     */
    public SseEmitter register(List<Trade> currentTrades) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        // Send snapshot before adding to shared list — avoids duplicate delivery
        for (Trade trade : currentTrades) {
            try {
                String json = objectMapper.writeValueAsString(trade);
                emitter.send(SseEmitter.event().name("trade_snapshot").data(json));
            } catch (Exception e) {
                log.warn("[TradeSseBroadcaster] Failed to send snapshot for trade id={}: {}",
                        trade.getId(), e.getMessage());
                emitter.complete();
                return emitter;
            }
        }
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> emitters.remove(emitter));
        emitter.onError(ex      -> emitters.remove(emitter));
        log.info("[TradeSseBroadcaster] New SSE client ({} total), sent {} snapshot trade(s)",
                emitters.size(), currentTrades.size());
        return emitter;
    }

    /** Broadcast a newly created trade. */
    public void broadcastNew(Trade trade) {
        broadcast("new_trade", trade);
    }

    /** Broadcast a status/field update for an existing trade. */
    public void broadcastUpdate(Trade trade) {
        broadcast("trade_update", trade);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void broadcast(String eventName, Trade trade) {
        if (emitters.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(trade);
        } catch (Exception e) {
            log.warn("[TradeSseBroadcaster] Serialisation failed for trade id={}: {}",
                    trade.getId(), e.getMessage());
            return;
        }
        send(eventName, json);
    }

    private void send(String eventName, String json) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
