package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.AngelLoginResponse;
import self.sai.stock.AlgoTrading.dto.LoginRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Calls AngelOne's loginByPassword REST API and delegates session persistence
 * to {@link SessionStoreService} (file-backed, survives restarts, 24-hour TTL).
 */
@Service
public class AngelOneAuthService {

    private static final Logger log       = LoggerFactory.getLogger(AngelOneAuthService.class);
    private static final String LOGIN_URL =
            "https://apiconnect.angelone.in/rest/auth/angelbroking/user/v1/loginByPassword";

    private final HttpClient      http;
    private final ObjectMapper    mapper;
    private final String          apiKey;
    private final SessionStoreService sessionStore;

    public AngelOneAuthService(SessionStoreService sessionStore,
                               @Value("${angelone.api.key:YOUR_API_KEY}") String apiKey) {
        this.sessionStore = sessionStore;
        this.apiKey       = apiKey;
        this.http         = HttpClient.newHttpClient();
        this.mapper       = new ObjectMapper();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Authenticates against AngelOne, stores the response, and returns it.
     *
     * @param request    login credentials
     * @param callerIp   IP of the caller (used for AngelOne request headers)
     * @return {@link AngelLoginResponse}, never {@code null} (contains error details on failure)
     */
    public AngelLoginResponse login(LoginRequest request, String callerIp) {
        String bodyJson;
        try {
            bodyJson = mapper.writeValueAsString(Map.of(
                    "clientcode", request.getClientcode(),
                    "password",   request.getPin(),
                    "totp",       request.getTotp()
            ));
        } catch (Exception e) {
            log.error("Failed to serialize login request: {}", e.getMessage());
            return errorResponse("Failed to build login request");
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(LOGIN_URL))
                .header("Content-Type",   "application/json")
                .header("Accept",         "application/json")
                .header("X-UserType",     "USER")
                .header("X-SourceID",     "WEB")
                .header("X-ClientLocalIP",  callerIp)
                .header("X-ClientPublicIP", callerIp)
                .header("X-MACAddress",   "00-00-00-00-00-00")
                .header("X-PrivateKey",   apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        try {
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            AngelLoginResponse parsed = mapper.readValue(response.body(), AngelLoginResponse.class);
            if (parsed != null && parsed.isStatus()) {
                sessionStore.save(request.getClientcode(), parsed);
                log.info("AngelOne login successful for clientcode: {}", request.getClientcode());
            } else {
                log.warn("AngelOne login failed for clientcode: {} — {}",
                        request.getClientcode(), parsed != null ? parsed.getMessage() : "null response");
            }
            return parsed;
        } catch (Exception e) {
            log.error("Error calling AngelOne login API: {}", e.getMessage(), e);
            return errorResponse("AngelOne API error: " + e.getMessage());
        }
    }

    /** Returns the stored session for a clientcode (or {@code null} if none/expired). */
    public AngelLoginResponse getSession(String clientcode) {
        return sessionStore.get(clientcode);
    }

    /** Returns auth status map for the given clientcode (or any active session). */
    public Map<String, Object> statusMap(String clientcode) {
        AngelLoginResponse session = clientcode != null && !clientcode.isBlank()
                ? sessionStore.get(clientcode)
                : sessionStore.getAnyActiveClientcode()
                        .map(cc -> sessionStore.get(cc)).orElse(null);
        boolean loggedIn = session != null && session.getData() != null;
        String cc = loggedIn ? clientcode != null && !clientcode.isBlank()
                ? clientcode
                : sessionStore.getAnyActiveClientcode().orElse("") : "";
        return Map.of("loggedIn", loggedIn, "clientcode", cc);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AngelLoginResponse errorResponse(String message) {
        AngelLoginResponse r = new AngelLoginResponse();
        r.setStatus(false);
        r.setMessage(message);
        return r;
    }
}
