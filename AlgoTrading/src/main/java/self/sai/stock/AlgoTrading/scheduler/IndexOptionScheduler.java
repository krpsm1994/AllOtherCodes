package self.sai.stock.AlgoTrading.scheduler;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import self.sai.stock.AlgoTrading.dto.CandleBar;
import self.sai.stock.AlgoTrading.service.IndexOptionScanService;
import self.sai.stock.AlgoTrading.service.MarketDataService;
import self.sai.stock.AlgoTrading.service.SettingService;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Intraday index option scheduler.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>At 8:55 AM (via {@link MarketSessionScheduler}): historical 5-min candles
 *       are pre-loaded by calling {@link #loadHistoricalIndexCandles(String)}.</li>
 *   <li>At 9:20 AM: the periodic scan timer is started.</li>
 *   <li>Every 5 minutes 9:20 → 15:30: today's latest 5-min candles are appended
 *       to the candleMap and {@link IndexOptionScanService#runIndexScan} is run.</li>
 *   <li>At 15:30 the timer shuts itself down.</li>
 * </ol>
 *
 * <p>On a mid-session server restart, {@code ApplicationReadyEvent} triggers a full
 * historical fetch + timer start, so no manual action is needed.
 */
@Component
@EnableScheduling
public class IndexOptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IndexOptionScheduler.class);

    private static final String CLIENT_CODE   = "S812559";
    private static final LocalTime SCAN_START = LocalTime.of(9, 20);
    private static final LocalTime SCAN_END   = LocalTime.of(15, 30);
    private static final ZoneId    IST        = ZoneId.of("Asia/Kolkata");

    @Autowired private SettingService        settingService;
    @Autowired private MarketDataService     marketDataService;
    @Autowired private IndexOptionScanService indexOptionScanService;

    /** Token → 5-min candle list, built from historical + today's appended bars. */
    private final Map<String, List<CandleBar>> candleMap = new ConcurrentHashMap<>();

    private ScheduledExecutorService timerExecutor;

    @PostConstruct
    public void init() {
        timerExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    // ── Scheduled entry points ────────────────────────────────────────────────

    /** At 9:20 AM IST on weekdays: start the scan timer. */
    @Scheduled(cron = "0 20 9 ? * MON-FRI", zone = "Asia/Kolkata")
    public void startScheduledIndexScan() {
        log.info("[IndexOptionScheduler] 9:20 AM triggered");
        if (candleMap.isEmpty()) {
            loadHistoricalIndexCandles(CLIENT_CODE);
        }
        scheduleTimer();
    }

    /**
     * On application startup: if we are within the scan window, pre-load historical
     * candles and start the timer (covers mid-session server restarts).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        LocalTime now = LocalTime.now(IST);
        if (!now.isBefore(SCAN_START) && !now.isAfter(SCAN_END)) {
            log.info("[IndexOptionScheduler] Startup within scan window — loading candles and starting timer");
            loadHistoricalIndexCandles(CLIENT_CODE);
            scheduleTimer();
        }
    }

    // ── Public method called by MarketSessionScheduler at 8:55 AM ────────────

    /**
     * Pre-loads full historical 5-min index candles so the scan has enough
     * data when the timer fires at 9:20 AM.
     * Called by {@link MarketSessionScheduler} during its morning startup sequence.
     */
    public void loadHistoricalIndexCandles(String clientcode) {
        log.info("[IndexOptionScheduler] Loading historical 5-min index candles…");
        try {
            Map<String, List<CandleBar>> historical =
                    marketDataService.fetchIndexCandles(clientcode, false);
            // Clear AFTER the fetch succeeds — avoids a window where the timer
            // thread sees an empty map while the multi-second API call is in-flight.
            candleMap.clear();
            candleMap.putAll(historical);
            log.info("[IndexOptionScheduler] Historical index candles loaded — {} token(s)", candleMap.size());
            candleMap.forEach((token, bars) ->
                    log.info("[IndexOptionScheduler]   token={} → {} candles", token, bars.size()));
        } catch (Exception e) {
            log.error("[IndexOptionScheduler] Failed to load historical index candles: {}", e.getMessage(), e);
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    private void scheduleTimer() {
        // Cancel any existing timer
        if (timerExecutor != null && !timerExecutor.isShutdown()) {
            timerExecutor.shutdownNow();
        }
        timerExecutor = Executors.newSingleThreadScheduledExecutor();

        int    numberOfCandles  = getNumberOfCandles();
        long   intervalMinutes  = 5L * numberOfCandles;                        // e.g. 75 min for 15 candles
        long   intervalMs       = intervalMinutes * 60 * 1000;
        LocalDateTime now       = LocalDateTime.now(IST);
        // Scans are always aligned to 9:20, 9:20+interval, 9:20+2*interval, …
        LocalDateTime firstRun  = LocalDateTime.of(LocalDate.now(IST), SCAN_START);

        // Advance to the next scan slot (aligned to 9:20 + N*intervalMinutes) that is strictly after now
        while (!firstRun.isAfter(now)) {
            firstRun = firstRun.plusMinutes(intervalMinutes);
        }

        long initialDelayMs = Duration.between(now, firstRun).toMillis();

        timerExecutor.scheduleAtFixedRate(() -> {
            LocalTime current = LocalTime.now(IST);
            if (current.isAfter(SCAN_END)) {
                log.info("[IndexOptionScheduler] 15:30 reached — stopping timer");
                timerExecutor.shutdown();
                return;
            }
            appendTodayCandles(CLIENT_CODE);
            indexOptionScanService.runIndexScan(candleMap, numberOfCandles);
            log.info("[IndexOptionScheduler] Scan tick at {}", current);
        }, initialDelayMs, intervalMs, TimeUnit.MILLISECONDS);

        log.info("[IndexOptionScheduler] Timer scheduled — first run at {}, interval {}min ({}candles)",
                firstRun.toLocalTime(), intervalMinutes, numberOfCandles);
    }

    // ── Candle helpers ────────────────────────────────────────────────────────

    /** Fetches today's 5-min index candles and appends new bars to the existing candleMap. */
    private void appendTodayCandles(String clientcode) {
        try {
            Map<String, List<CandleBar>> todayMap =
                    marketDataService.fetchIndexCandles(clientcode, true);

            todayMap.forEach((token, newBars) -> {
                List<CandleBar> existing = candleMap.computeIfAbsent(token, k -> new ArrayList<>());
                Set<LocalDateTime> existingTs = existing.stream()
                        .map(CandleBar::getTimestamp)
                        .collect(Collectors.toSet());
                newBars.stream()
                        .filter(b -> !existingTs.contains(b.getTimestamp()))
                        .forEach(existing::add);
            });
            log.debug("[IndexOptionScheduler] Today's index candles appended");
        } catch (Exception e) {
            log.error("[IndexOptionScheduler] Failed to append today's index candles: {}", e.getMessage(), e);
        }
    }

    private int getNumberOfCandles() {
        return settingService.getInt(SettingService.GRP_ALGO_SCAN, "numberOfCandles", 15);
    }
}
