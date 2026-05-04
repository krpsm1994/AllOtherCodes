package self.sai.stock.AlgoTrading.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import self.sai.stock.AlgoTrading.entity.Trade;
import self.sai.stock.AlgoTrading.repository.TradeRepository;
import self.sai.stock.AlgoTrading.service.TradeMonitorService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoints for the trades table.
 *
 * GET    /api/trades      — returns all trades
 * POST   /api/trades      — create a new trade
 * PUT    /api/trades/{id} — update an existing trade
 */
@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeRepository tradeRepository;
    private final TradeMonitorService tradeMonitorService;

    public TradeController(TradeRepository tradeRepository, TradeMonitorService tradeMonitorService) {
        this.tradeRepository = tradeRepository;
        this.tradeMonitorService = tradeMonitorService;
    }

    @GetMapping
    public ResponseEntity<List<Trade>> getTrades() {
        return ResponseEntity.ok(tradeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Trade> createTrade(@RequestBody Trade trade) {
        trade.setId(null); // ensure new record
        if (trade.getDate() == null || trade.getDate().trim().isEmpty()) {
            trade.setDate(LocalDate.now().toString());
        }
        Trade saved = tradeRepository.save(trade);
        tradeMonitorService.registerTrade(saved);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Trade> updateTrade(@PathVariable Long id, @RequestBody Trade trade) {
        Optional<Trade> existing = tradeRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        trade.setId(id);
        if (trade.getDate() == null || trade.getDate().trim().isEmpty()) {
            trade.setDate(existing.get().getDate());
        }
        Trade saved = tradeRepository.save(trade);
        tradeMonitorService.registerTrade(saved);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrade(@PathVariable Long id) {
        if (!tradeRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tradeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
