package self.sai.stock.AlgoTrading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.ZerodhaSessionData;

/**
 * In-memory store for the current Zerodha Kite Connect session.
 * Zerodha access tokens expire at midnight IST (treated as 24 h here).
 */
@Service
public class ZerodhaSessionStoreService {

    private static final Logger log    = LoggerFactory.getLogger(ZerodhaSessionStoreService.class);
    private static final long   TTL_MS = 24L * 60 * 60 * 1_000;

    private final SessionStoreService sessionStore;

    private volatile String accessToken;
    private volatile String publicToken;
    private volatile String userId;
    private volatile String userName;
    private volatile String apiKey;
    private volatile long   loginTimeEpochMs = 0;

    public ZerodhaSessionStoreService(@Lazy SessionStoreService sessionStore) {
        this.sessionStore = sessionStore;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Saves the Zerodha session in memory and attaches it to the AngelOne session on disk. */
    public void save(ZerodhaSessionData data) {
        save(data, null);
    }

    /** Saves the Zerodha session, recording which API key was used for authentication. */
    public void save(ZerodhaSessionData data, String usedApiKey) {
        this.accessToken      = data.getAccessToken();
        this.publicToken      = data.getPublicToken();
        this.userId           = data.getUserId();
        this.userName         = data.getUserName();
        this.apiKey           = usedApiKey;
        this.loginTimeEpochMs = System.currentTimeMillis();
        log.info("Zerodha session saved — userId={} userName={}", userId, userName);
        // Persist alongside AngelOne session
        try {
            sessionStore.getAnyActiveClientcode().ifPresent(cc ->
                    sessionStore.attachZerodhaSession(cc, data));
        } catch (Exception e) {
            log.warn("Could not attach Zerodha session to disk: {}", e.getMessage());
        }
    }

    /** Returns the access token if still valid, else {@code null}. */
    public String getAccessToken() { return isValid() ? accessToken : null; }
    public String getPublicToken() { return isValid() ? publicToken : null; }
    public String getUserId()      { return userId; }
    public String getUserName()    { return userName; }
    public String getApiKey()      { return apiKey; }

    public boolean isValid() {
        return accessToken != null
                && !accessToken.isBlank()
                && (System.currentTimeMillis() - loginTimeEpochMs) < TTL_MS;
    }

    /** Returns a status map suitable for JSON responses. */
    public java.util.Map<String, Object> statusMap() {
        return java.util.Map.of(
                "loggedIn",    isValid(),
                "userId",      userId    != null ? userId    : "",
                "userName",    userName  != null ? userName  : "",
                "loginTimeMs", loginTimeEpochMs
        );
    }

    public void clear() {
        accessToken = publicToken = userId = userName = apiKey = null;
        loginTimeEpochMs = 0;
        log.info("Zerodha session cleared.");
    }
}
