package self.sai.stock.AlgoTrading.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import self.sai.stock.AlgoTrading.entity.Instrument;
import self.sai.stock.AlgoTrading.repository.InstrumentRepository;
import self.sai.stock.AlgoTrading.service.InstrumentRefreshService;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the instruments master data.
 *
 * <pre>
 * POST /api/instruments/refresh   — fetch AngelOne ScripMaster, apply watchlist filter,
 *                                   truncate + reload instruments table
 * </pre>
 *
 * Response (200 OK):  {@code { "status": "ok", "saved": 142 }}
 * Response (500):     {@code { "status": "error", "message": "..." }}
 */
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private static final Logger log = LoggerFactory.getLogger(InstrumentController.class);

    private final InstrumentRefreshService instrumentRefreshService;
    private final InstrumentRepository      instrumentRepository;

    public InstrumentController(InstrumentRefreshService instrumentRefreshService,
                                InstrumentRepository instrumentRepository) {
        this.instrumentRefreshService = instrumentRefreshService;
        this.instrumentRepository     = instrumentRepository;
    }

    /** Returns all instruments sorted by name (for the candles-tab dropdown). */
    @GetMapping
    public ResponseEntity<List<Instrument>> getAll() {
        List<Instrument> all = instrumentRepository.findAll();
        all.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return ResponseEntity.ok(all);
    }

    /**
     * Fetches the AngelOne ScripMaster, filters by watchlist names from
     * {@code instruments.txt} (NSE equity only), wipes the existing
     * {@code instruments} table, and replaces it with fresh data.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            int saved = instrumentRefreshService.refresh();
            log.info("Instruments refresh succeeded: {} rows saved", saved);
            return ResponseEntity.ok(Map.of("status", "ok", "saved", saved));
        } catch (Exception e) {
            log.error("Instruments refresh failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
