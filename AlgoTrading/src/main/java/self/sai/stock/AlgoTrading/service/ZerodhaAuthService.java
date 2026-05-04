package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.ZerodhaSessionData;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Handles the Zerodha Kite Connect OAuth token-exchange flow.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>User opens: {@code https://kite.zerodha.com/connect/login?v=3&api_key=<key>}</li>
 *   <li>Zerodha redirects back with a {@code request_token} query param.</li>
 *   <li>Call {@link #exchangeToken(String, boolean)} to get a persistent {@code access_token}.</li>
 * </ol>
 */
@Service
public class ZerodhaAuthService {

    private static final Logger log          = LoggerFactory.getLogger(ZerodhaAuthService.class);
    private static final String EXCHANGE_URL = "https://api.kite.trade/session/token";

    private final HttpClient             http;
    private final ObjectMapper           mapper;
    private final ZerodhaSessionStoreService sessionStore;
    private final String                 apiKey;
    private final String                 apiSecret;
    private final String                 localhostApiKey;
    private final String                 localhostApiSecret;

    public ZerodhaAuthService(ZerodhaSessionStoreService sessionStore,
                              @Value("${zerodha.api.key:}")              String apiKey,
                              @Value("${zerodha.api.secret:}")           String apiSecret,
                              @Value("${zerodha.localhost.api.key:}")    String localhostApiKey,
                              @Value("${zerodha.localhost.api.secret:}") String localhostApiSecret) {
        this.sessionStore       = sessionStore;
        this.apiKey             = apiKey;
        this.apiSecret          = apiSecret;
        this.localhostApiKey    = localhostApiKey;
        this.localhostApiSecret = localhostApiSecret;
        this.http               = HttpClient.newHttpClient();
        this.mapper             = new ObjectMapper();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the Kite login URL for the correct environment. */
    public String getLoginUrl(boolean localhost) {
        return "https://kite.zerodha.com/connect/login?v=3&api_key=" + getApiKey(localhost);
    }

    /** Returns the API key appropriate for the given environment. */
    public String getApiKey(boolean localhost) {
        return localhost ? localhostApiKey : apiKey;
    }

    /**
     * Exchanges a one-time {@code request_token} for a persistent {@code access_token}.
     *
     * @param requestToken the token from the Kite OAuth redirect
     * @param localhost    {@code true} to use dev credentials
     * @return result map — always has a {@code "success"} boolean key
     */
    public Map<String, Object> exchangeToken(String requestToken, boolean localhost) {
        if (requestToken == null || requestToken.isBlank()) {
            return Map.of("success", false, "message", "request_token is required");
        }
        String effectiveKey    = localhost ? localhostApiKey    : apiKey;
        String effectiveSecret = localhost ? localhostApiSecret : apiSecret;

        if (effectiveSecret == null || effectiveSecret.isBlank()) {
            log.error("zerodha.api.secret not configured — cannot exchange token");
            return Map.of("success", false, "message",
                    "Zerodha API secret not configured. Set -Dzerodha.api.secret=...");
        }

        try {
            String checksum = sha256(effectiveKey + requestToken + effectiveSecret);
            String form = "api_key="       + URLEncoder.encode(effectiveKey, StandardCharsets.UTF_8)
                        + "&request_token=" + URLEncoder.encode(requestToken, StandardCharsets.UTF_8)
                        + "&checksum="      + URLEncoder.encode(checksum,     StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXCHANGE_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("X-Kite-Version", "3")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            KiteTokenResponse parsed = mapper.readValue(response.body(), KiteTokenResponse.class);
            if (parsed != null && "success".equalsIgnoreCase(parsed.getStatus()) && parsed.getData() != null) {
                ZerodhaSessionData data = parsed.getData();
                sessionStore.save(data, effectiveKey);
                log.info("Zerodha token exchange successful — userId={}", data.getUserId());
                return Map.of(
                        "success",     true,
                        "accessToken", data.getAccessToken() != null ? data.getAccessToken() : "",
                        "userId",      data.getUserId()      != null ? data.getUserId()      : "",
                        "userName",    data.getUserName()    != null ? data.getUserName()    : ""
                );
            } else {
                String msg = parsed != null ? parsed.getMessage() : "Empty response";
                log.warn("Zerodha token exchange failed: {}", msg);
                return Map.of("success", false, "message", msg != null ? msg : "Token exchange failed");
            }
        } catch (Exception e) {
            log.error("Error exchanging Zerodha token: {}", e.getMessage(), e);
            return Map.of("success", false, "message", "Token exchange error: " + e.getMessage());
        }
    }

    /** Returns current Zerodha session status map. */
    public Map<String, Object> sessionStatus() {
        return sessionStore.statusMap();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    // ── Internal DTO (Kite token-exchange response) ────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KiteTokenResponse {
        private String           status;
        private String           message;
        private ZerodhaSessionData data;

        public String             getStatus()             { return status; }
        public void               setStatus(String s)     { this.status = s; }
        public String             getMessage()            { return message; }
        public void               setMessage(String m)   { this.message = m; }
        public ZerodhaSessionData getData()               { return data; }
        public void               setData(ZerodhaSessionData d) { this.data = d; }
    }
}
