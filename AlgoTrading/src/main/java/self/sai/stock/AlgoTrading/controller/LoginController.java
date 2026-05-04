package self.sai.stock.AlgoTrading.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import self.sai.stock.AlgoTrading.dto.AngelLoginResponse;
import self.sai.stock.AlgoTrading.dto.LoginRequest;
import self.sai.stock.AlgoTrading.service.AngelOneAuthService;
import self.sai.stock.AlgoTrading.service.SessionStoreService;
import self.sai.stock.AlgoTrading.service.ZerodhaAuthService;

import java.util.Map;

/**
 * REST controller for AngelOne and Zerodha broker authentication.
 * All endpoints require a valid JWT in the {@code Authorization: Bearer <token>} header
 * (enforced by {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/broker")
public class LoginController {

    private final AngelOneAuthService angelService;
    private final ZerodhaAuthService  zerodhaService;
    private final SessionStoreService sessionStore;

    public LoginController(AngelOneAuthService angelService,
                           ZerodhaAuthService  zerodhaService,
                           SessionStoreService sessionStore) {
        this.angelService   = angelService;
        this.zerodhaService = zerodhaService;
        this.sessionStore   = sessionStore;
    }

    // ── AngelOne ──────────────────────────────────────────────────────────────

    /**
     * Returns whether a valid AngelOne session exists.
     * GET /api/broker/angel/status?clientcode=S812559
     */
    @GetMapping("/angel/status")
    public ResponseEntity<Map<String, Object>> angelStatus(
            @RequestParam(required = false) String clientcode) {
        return ResponseEntity.ok(angelService.statusMap(clientcode));
    }

    /**
     * Authenticates with AngelOne using clientcode + PIN + TOTP.
     * POST /api/broker/angel/login
     * Body: { "clientcode": "...", "pin": "...", "totp": "..." }
     */
    @PostMapping("/angel/login")
    public ResponseEntity<AngelLoginResponse> angelLogin(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Real-IP", defaultValue = "127.0.0.1") String callerIp) {
        AngelLoginResponse resp = angelService.login(request, callerIp);
        return ResponseEntity.ok(resp);
    }

    /**
     * Returns the stored AngelOne session for a clientcode.
     * GET /api/broker/angel/session?clientcode=S812559
     */
    @GetMapping("/angel/session")
    public ResponseEntity<?> angelSession(@RequestParam String clientcode) {
        AngelLoginResponse session = angelService.getSession(clientcode);
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(session);
    }

    // ── Zerodha ───────────────────────────────────────────────────────────────

    /**
     * Returns the Kite Connect login URL. Localhost is auto-detected from the
     * Origin / Referer / Host header — no frontend param needed.
     * GET /api/broker/zerodha/login-url
     */
    @GetMapping("/zerodha/login-url")
    public ResponseEntity<Map<String, String>> zerodhaLoginUrl(HttpServletRequest request) {
        boolean localhost = isLocalhost(request);
        return ResponseEntity.ok(Map.of("url", zerodhaService.getLoginUrl(localhost)));
    }

    /**
     * Returns the Zerodha API key for the current environment.
     * GET /api/broker/zerodha/api-key
     */
    @GetMapping("/zerodha/api-key")
    public ResponseEntity<Map<String, String>> zerodhaApiKey(HttpServletRequest request) {
        boolean localhost = isLocalhost(request);
        return ResponseEntity.ok(Map.of("apiKey", zerodhaService.getApiKey(localhost)));
    }

    /**
     * Exchanges a one-time request_token for a persistent access_token.
     * Accepts the token as a query param (for frontend redirect callback).
     * POST /api/broker/zerodha/exchange?request_token=...
     */
    @PostMapping("/zerodha/exchange")
    public ResponseEntity<Map<String, Object>> zerodhaExchange(
            @RequestParam("request_token") String requestToken,
            HttpServletRequest request) {
        boolean localhost = isLocalhost(request);
        Map<String, Object> result = zerodhaService.exchangeToken(requestToken, localhost);
        if (Boolean.TRUE.equals(result.get("success"))) {
            sessionStore.getAnyActiveClientcode().ifPresent(cc -> {
                self.sai.stock.AlgoTrading.dto.ZerodhaSessionData dto =
                        new self.sai.stock.AlgoTrading.dto.ZerodhaSessionData();
                Object at  = result.get("accessToken");
                Object uid = result.get("userId");
                Object un  = result.get("userName");
                if (at  != null) dto.setAccessToken(String.valueOf(at));
                if (uid != null) dto.setUserId(String.valueOf(uid));
                if (un  != null) dto.setUserName(String.valueOf(un));
                sessionStore.attachZerodhaSession(cc, dto);
            });
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the current Zerodha session status (includes loginTimeMs for polling).
     * GET /api/broker/zerodha/status
     */
    @GetMapping("/zerodha/status")
    public ResponseEntity<Map<String, Object>> zerodhaStatus() {
        return ResponseEntity.ok(zerodhaService.sessionStatus());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Auto-detects whether the request originated from localhost. */
    private boolean isLocalhost(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null) return origin.contains("localhost") || origin.contains("127.0.0.1");
        String referer = req.getHeader("Referer");
        if (referer != null) return referer.contains("localhost") || referer.contains("127.0.0.1");
        String host = req.getHeader("Host");
        return host != null && (host.startsWith("localhost") || host.startsWith("127.0.0.1"));
    }
}
