package self.sai.stock.AlgoTrading.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import self.sai.stock.AlgoTrading.service.ZerodhaAuthService;

import java.util.Map;

/**
 * Handles the Zerodha OAuth redirect callback.
 *
 * Zerodha redirects the browser to:
 *   http(s)://your-host/zerodha/callback?request_token=TOKEN&action=login&status=success
 *
 * This controller:
 *   1. Exchanges the one-time request_token for a persistent access_token.
 *   2. Redirects the user to /dashboard (Angular SPA picks up the success state via polling).
 *
 * No JWT is required — this is a public browser redirect.
 */
@Controller
public class ZerodhaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(ZerodhaCallbackController.class);

    private final ZerodhaAuthService zerodhaService;

    public ZerodhaCallbackController(ZerodhaAuthService zerodhaService) {
        this.zerodhaService = zerodhaService;
    }

    @GetMapping("/zerodha/callback")
    public String callback(
            @RequestParam(value = "request_token", required = false) String requestToken,
            @RequestParam(value = "status",        defaultValue = "")  String status,
            HttpServletRequest request) {

        if (requestToken == null || requestToken.isBlank()) {
            log.warn("Zerodha callback received without request_token (status={})", status);
            return "redirect:/dashboard?zerodha=error";
        }

        boolean localhost = isLocalhost(request);
        log.info("Zerodha callback: status={} localhost={}", status, localhost);

        try {
            Map<String, Object> result = zerodhaService.exchangeToken(requestToken, localhost);
            if (Boolean.TRUE.equals(result.get("success"))) {
                log.info("Zerodha token exchange succeeded — redirecting to dashboard");
                return "redirect:/dashboard";
            } else {
                String msg = String.valueOf(result.getOrDefault("message", "exchange_failed"));
                log.warn("Zerodha token exchange failed: {}", msg);
                return "redirect:/dashboard?zerodha=error";
            }
        } catch (Exception e) {
            log.error("Zerodha callback error: {}", e.getMessage(), e);
            return "redirect:/dashboard?zerodha=error";
        }
    }

    private boolean isLocalhost(HttpServletRequest req) {
        String origin = req.getHeader("Origin");
        if (origin != null) return origin.contains("localhost") || origin.contains("127.0.0.1");
        String referer = req.getHeader("Referer");
        if (referer != null) return referer.contains("localhost") || referer.contains("127.0.0.1");
        String host = req.getHeader("Host");
        return host != null && (host.startsWith("localhost") || host.startsWith("127.0.0.1"));
    }
}
