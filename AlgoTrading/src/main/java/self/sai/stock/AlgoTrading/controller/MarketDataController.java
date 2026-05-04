package self.sai.stock.AlgoTrading.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import self.sai.stock.AlgoTrading.dto.CandleBar;
import self.sai.stock.AlgoTrading.dto.CandleRowDto;
import self.sai.stock.AlgoTrading.entity.Candle;
import self.sai.stock.AlgoTrading.repository.CandleRepository;
import self.sai.stock.AlgoTrading.service.MarketDataService;
import self.sai.stock.AlgoTrading.scheduler.MarketSessionScheduler;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST endpoint to trigger a full historical candle refresh.
 *
 * <pre>
 * POST /api/market-data/fetch?clientcode=S812559
 * Authorization: Bearer &lt;jwt&gt;
 * </pre>
 *
 * Response (200 OK):  {@code { "status": "ok", "saved": 31542 }}
 * Response (500):     {@code { "status": "error", "message": "..." }}
 *
 * <p>The endpoint is synchronous (blocks until all instruments are fetched).
 * For 20 instruments × 3 chunks × ~300 ms/call ≈ ~18 seconds.
 */
@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private static final Logger log = LoggerFactory.getLogger(MarketDataController.class);

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MarketDataService marketDataService;
    private final CandleRepository  candleRepository;
    private final MarketSessionScheduler marketSessionScheduler;

    public MarketDataController(MarketDataService marketDataService,
                                CandleRepository candleRepository,
                                MarketSessionScheduler marketSessionScheduler) {
        this.marketDataService       = marketDataService;
        this.candleRepository        = candleRepository;
        this.marketSessionScheduler  = marketSessionScheduler;
    }

    /**
     * Fetches 75 days of 10-min OHLCV candles for all {@code 10MinWatchlist}
     * instruments and saves them to the {@code candles} table (truncates first).
     *
     * @param clientcode AngelOne clientcode whose active session provides the JWT
     */
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchCandles(@RequestParam String clientcode) {
        try {
            int saved = marketDataService.fetchCandles(clientcode);
            log.info("Market data fetch completed: {} candle rows saved", saved);
            return ResponseEntity.ok(Map.of("status", "ok", "saved", saved));
        } catch (Exception e) {
            log.error("Market data fetch failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * Returns all 10-min candle rows from the DB for a given token.
     *
     * <pre>GET /api/candles?token=12345</pre>
     */
    @GetMapping("/candles")
    public ResponseEntity<List<CandleRowDto>> getCandlesByToken(@RequestParam String token) {
        // Prefer live in-memory candles from the scheduler; fall back to DB
        List<CandleBar> liveBars = marketSessionScheduler.getCandlesForToken(token);
        if (!liveBars.isEmpty()) {
            List<CandleRowDto> result = liveBars.stream()
                    .map(b -> new CandleRowDto(
                            b.getTimestamp().format(DT_FMT),
                            b.getOpen(), b.getHigh(), b.getLow(), b.getClose(), b.getVolume()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        }
        // Fallback: serve from DB (before market opens / scheduler not yet loaded)
        List<Candle> candles = candleRepository.findByTokenOrderByDateAsc(token);
        List<CandleRowDto> result = candles.stream()
                .map(c -> new CandleRowDto(c.getDate(), c.getOpen(), c.getHigh(),
                                           c.getLow(), c.getClose(), 0L))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Fetches 1 year of daily OHLCV candles from AngelOne for a given token.
     * Data is NOT stored in the database.
     *
     * <pre>GET /api/market-data/daily?token=12345&clientcode=S812559</pre>
     */
    @GetMapping("/daily")
    public ResponseEntity<?> getDailyCandles(@RequestParam String token,
                                             @RequestParam String clientcode) {
        try {
            List<CandleBar> bars = marketDataService.fetchDailyCandles(clientcode, token);
            List<CandleRowDto> result = bars.stream()
                    .map(b -> new CandleRowDto(
                            b.getTimestamp().format(DT_FMT),
                            b.getOpen(), b.getHigh(), b.getLow(), b.getClose(), b.getVolume()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Daily candle fetch failed for token {}: {}", token, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
