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
import self.sai.stock.AlgoTrading.dto.CandleBar;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@EnableScheduling
public class MarketDailyCandleScanScheduler {
	
	private static final Logger log = LoggerFactory.getLogger(MarketDailyCandleScanScheduler.class);
	
	private static final String CLIENT_CODE = "S812559";
	 
    @Autowired
    private SettingService settingService;

    @Autowired
    private MarketDataService marketDataService;
    
    @Autowired
    private AlgoScanService algoScanService;

    private Map<String, List<CandleBar>> candleMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService timerExecutor;

    @PostConstruct
    public void init() {
        timerExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    // Disabled — DailyWatchlist replaced by Index option intraday scan (IndexOptionScheduler)
    // @Scheduled(cron = "0 0 16 ? * MON-FRI", zone = "Asia/Kolkata")
    public void scheduleMarketSessionTasks() {
        log.info("[Scheduler] 04:00 PM triggered. Fetching settings and candles...");
        int numberOfCandles = getNumberOfCandlesFromSettings();
        log.info("[Scheduler] numberOfCandles from settings: {}", numberOfCandles);
        fetchDailyCandles();
        algoScanService.runAlgoScan(candleMap, numberOfCandles);
    }
    
    // Disabled — replaced by IndexOptionScheduler startup check
    // @org.springframework.context.event.EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Kolkata"));
        LocalTime start = LocalTime.of(16, 00);
        LocalTime end = LocalTime.of(23, 59);

        // If server starts between 9:15 AM and 3:30 PM, trigger the logic
        if (!now.isBefore(start) && !now.isAfter(end)) {
        	log.info("Market is closed. Running Daily scan...");
            int numberOfCandles = getNumberOfCandlesFromSettings();
            log.info("[Scheduler] numberOfCandles from settings: {}", numberOfCandles);fetchDailyCandles();
            fetchDailyCandles();
            algoScanService.runAlgoScan(candleMap, numberOfCandles);
        }
    }

    private int getNumberOfCandlesFromSettings() {
        return settingService.getInt(SettingService.GRP_ALGO_SCAN, "numberOfCandles", 15);
    }

    private void fetchDailyCandles() {
        candleMap.clear();
        try {
        	
            candleMap = marketDataService.fetchDailyCandles(CLIENT_CODE);
            log.info("[Scheduler] Fetched and stored old session candles for instruments.");
        } catch (Exception e) {
            log.error("[Scheduler] Error fetching candles: {}", e.getMessage(), e);
        }
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
}
