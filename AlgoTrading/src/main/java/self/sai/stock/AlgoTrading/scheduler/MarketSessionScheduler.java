package self.sai.stock.AlgoTrading.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import self.sai.stock.AlgoTrading.service.AlgoScanService;
import self.sai.stock.AlgoTrading.service.SettingService;
import self.sai.stock.AlgoTrading.service.MarketDataService;
import self.sai.stock.AlgoTrading.service.TickStreamService;
import self.sai.stock.AlgoTrading.dto.CandleBar;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@EnableScheduling
public class MarketSessionScheduler {
	
	private static final Logger log = LoggerFactory.getLogger(MarketSessionScheduler.class);
	
	private static final String CLIENT_CODE = "S812559";
	 
    @Autowired
    private SettingService settingService;

    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private AlgoScanService algoScanService;

    @Autowired
    private TickStreamService tickStreamService;

    private Map<String, List<CandleBar>> candleMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService timerExecutor;

    @PostConstruct
    public void init() {
        timerExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Scheduled(cron = "0 55 8 ? * MON-FRI", zone = "Asia/Kolkata")
    public void scheduleMarketSessionTasks() {
        log.info("[Scheduler] 8:55 AM triggered. Fetching settings and candles...");
        int numberOfCandles = getNumberOfCandlesFromSettings();
        log.info("[Scheduler] numberOfCandles from settings: {}", numberOfCandles);
        fetchAndStoreHistoricalCandles();
        startStream();
        scheduleTimer(numberOfCandles);
    }
    
 // This runs automatically whenever the server starts/restarts
    @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        LocalTime start = LocalTime.of(9, 15);
        LocalTime end = LocalTime.of(15, 30);

        // If server starts between 9:15 AM and 3:30 PM, trigger the logic
        if (!now.isBefore(start) && !now.isAfter(end)) {
        	log.info("Market is active. Running missed startup task...");
            int numberOfCandles = getNumberOfCandlesFromSettings();
            log.info("[Scheduler] numberOfCandles from settings: {}", numberOfCandles);
            fetchAndStoreHistoricalCandles();
            startStream();
            scheduleTimer(numberOfCandles);
        }
    }

    private void startStream() {
        if (tickStreamService.isStreaming()) {
            log.info("[Scheduler] TickStream already running, skipping start.");
            return;
        }
        try {
            tickStreamService.start(CLIENT_CODE);
            log.info("[Scheduler] TickStream started for clientcode={}", CLIENT_CODE);
        } catch (Exception e) {
            log.error("[Scheduler] Failed to start TickStream: {}", e.getMessage(), e);
        }
    }

    private int getNumberOfCandlesFromSettings() {
        return settingService.getInt(SettingService.GRP_ALGO_SCAN, "numberOfCandles", 15);
    }

    private void fetchAndStoreHistoricalCandles() {
        candleMap.clear();
        try {
            candleMap = marketDataService.fetchLastNSessionCandles(CLIENT_CODE, false);
            log.info("[Scheduler] Fetched and stored last 60 session candles for instruments.");
        } catch (Exception e) {
            log.error("[Scheduler] Error fetching candles: {}", e.getMessage(), e);
        }
    }

    /** Returns live in-memory candles for the given token, or empty list if not loaded. */
    public List<CandleBar> getCandlesForToken(String token) {
        return candleMap.getOrDefault(token, Collections.emptyList());
    }
    
    private void fetchTodayCandles() {
    	try {
    		Map<String, List<CandleBar>> todayCandleMap = marketDataService.fetchLastNSessionCandles(CLIENT_CODE, true);
    		todayCandleMap.forEach((symbol, newBars) -> {
                // Get existing list or initialize if stock is new
                List<CandleBar> existingBars = candleMap.getOrDefault(symbol, new ArrayList<>());

                // 1. Map existing timestamps for O(1) lookup
                Set<LocalDateTime> existingTimestamps = existingBars.stream()
                        .map(CandleBar::getTimestamp)
                        .collect(Collectors.toSet());

                // 2. Filter out bars that already exist based on timestamp
                List<CandleBar> uniqueNewBars = newBars.stream()
                        .filter(bar -> !existingTimestamps.contains(bar.getTimestamp()))
                        .collect(Collectors.toList());

                // 3. Append unique bars to the main list
                existingBars.addAll(uniqueNewBars);
                
                // 4. Put back in case it was a new stock entry
                candleMap.put(symbol, existingBars);
            });
            log.info("[Scheduler] Fetched and stored last 60 session candles for instruments.");
        } catch (Exception e) {
            log.error("[Scheduler] Error fetching candles: {}", e.getMessage(), e);
        }
    }

    private void scheduleTimer(int numberOfCandles) {
        if (timerExecutor == null || timerExecutor.isShutdown()) {
            timerExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        // Cancel previous tasks
        timerExecutor.shutdownNow();
        timerExecutor = Executors.newSingleThreadScheduledExecutor();

        LocalTime start = LocalTime.of(9, 25);
        LocalTime end = LocalTime.of(15, 30);
        int intervalMinutes = 10 * numberOfCandles;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDateTime firstRun = LocalDateTime.of(today, start);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
        if (now.isAfter(firstRun)) {
            while (firstRun.isBefore(now)) {
                firstRun = firstRun.plusMinutes(intervalMinutes);
            }
        }
        long initialDelay = Duration.between(now, firstRun).toMillis();
        long period = intervalMinutes * 60 * 1000L;

        timerExecutor.scheduleAtFixedRate(() -> {
            LocalTime current = LocalTime.now(ZoneId.of("Asia/Kolkata"));
            if (current.isAfter(end)) {
                log.info("[Scheduler] Timer reached end time. Cancelling further executions.");
                timerExecutor.shutdown();
                if(tickStreamService.isStreaming()){
                    tickStreamService.stop();
                    log.info("[Scheduler] TickStream stopped as market session ended.");
                }
                return;
            }
            fetchTodayCandles();
            algoScanService.runAlgoScan(candleMap, numberOfCandles);
            log.info("[Scheduler] Timer tick at {} (interval {} min)", current, intervalMinutes);
        }, initialDelay, period, TimeUnit.MILLISECONDS);
        log.info("[Scheduler] Timer scheduled: every {} min from {} to {}", intervalMinutes, start, end);
    }
}
