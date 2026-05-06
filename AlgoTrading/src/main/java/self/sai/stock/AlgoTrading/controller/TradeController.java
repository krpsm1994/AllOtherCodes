package self.sai.stock.AlgoTrading.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import self.sai.stock.AlgoTrading.entity.Trade;
import self.sai.stock.AlgoTrading.repository.TradeRepository;
import self.sai.stock.AlgoTrading.service.TradeSseBroadcaster;
import self.sai.stock.AlgoTrading.service.TradeMonitorService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * REST endpoints for the trades table.
 *
 * GET    /api/trades        — returns all trades
 * GET    /api/trades/stream — SSE stream: sends current snapshot on connect, then live updates
 * POST   /api/trades        — create a new trade
 * PUT    /api/trades/{id}   — update an existing trade
 */
@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeRepository tradeRepository;
    private final TradeMonitorService tradeMonitorService;
    private final TradeSseBroadcaster tradeSseBroadcaster;

    public TradeController(TradeRepository tradeRepository,
                           TradeMonitorService tradeMonitorService,
                           TradeSseBroadcaster tradeSseBroadcaster) {
        this.tradeRepository     = tradeRepository;
        this.tradeMonitorService = tradeMonitorService;
        this.tradeSseBroadcaster = tradeSseBroadcaster;
    }

    @GetMapping
    public ResponseEntity<List<Trade>> getTrades() {
        return ResponseEntity.ok(tradeRepository.findAll());
    }

    /**
     * SSE endpoint — subscribe here to receive all current trades immediately
     * (as {@code trade_snapshot} events), then live {@code new_trade} /
     * {@code trade_update} events as trades change.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return tradeSseBroadcaster.register(tradeRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Trade> createTrade(@RequestBody Trade trade) {
        trade.setId(null); // ensure new record
        if (trade.getDate() == null || trade.getDate().trim().isEmpty()) {
            trade.setDate(LocalDate.now().toString());
        }
        Trade saved = tradeRepository.save(trade);
        tradeMonitorService.registerTrade(saved);
        tradeSseBroadcaster.broadcastNew(saved);
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
        tradeSseBroadcaster.broadcastUpdate(saved);
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

    @PostMapping("/{id}/buy")
    public ResponseEntity<Trade> manualBuyTrade(@PathVariable Long id) {
        Optional<Trade> existing = tradeRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Trade trade = existing.get();
        if (!"WATCHING".equals(trade.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        Trade updated = tradeMonitorService.placeManualBuyOrder(trade);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/sell")
    public ResponseEntity<Trade> manualSellTrade(@PathVariable Long id) {
        Optional<Trade> existing = tradeRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Trade trade = existing.get();
        if (!"OPEN".equals(trade.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        Trade updated = tradeMonitorService.placeManualSellOrder(trade);
        return ResponseEntity.ok(updated);
    }
}
