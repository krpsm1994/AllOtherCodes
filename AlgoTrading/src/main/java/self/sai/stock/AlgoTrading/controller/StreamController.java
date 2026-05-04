package self.sai.stock.AlgoTrading.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import self.sai.stock.AlgoTrading.service.TickStreamService;

import java.util.Map;

/**
 * REST controller to start and stop the AngelOne live tick stream.
 *
 * POST /api/stream/start?clientcode=S812559   — connect and subscribe all instruments
 * POST /api/stream/stop                       — disconnect
 * GET  /api/stream/status                     — check if streaming is active
 */
@RestController
@RequestMapping("/api/stream")
public class StreamController {

    private final TickStreamService tickStreamService;

    public StreamController(TickStreamService tickStreamService) {
        this.tickStreamService = tickStreamService;
    }

    /**
     * Start streaming ticks for all instruments in the DB.
     * Requires an active AngelOne session (login first via POST /api/broker/angel/login).
     *
     * @param clientcode AngelOne clientcode whose session will be used for feed authentication
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestParam String clientcode) {
        if (tickStreamService.isStreaming()) {
            return ResponseEntity.ok(Map.of("streaming", true, "message", "Already streaming"));
        }
        try {
            tickStreamService.start(clientcode);
            return ResponseEntity.ok(Map.of("streaming", true, "message", "Stream started"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("streaming", false, "message", e.getMessage()));
        }
    }

    /** Stop the live tick stream. */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        tickStreamService.stop();
        return ResponseEntity.ok(Map.of("streaming", false, "message", "Stream stopped"));
    }

    /** Returns the current streaming status. */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("streaming", tickStreamService.isStreaming()));
    }
}
