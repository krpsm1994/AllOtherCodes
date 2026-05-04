package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.AngelLoginResponse;
import self.sai.stock.AlgoTrading.dto.PersistedSession;
import self.sai.stock.AlgoTrading.dto.ZerodhaSessionData;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Persists AngelOne (and optionally Zerodha) sessions to a JSON file on disk
 * so that they survive application restarts. Sessions expire after 24 hours.
 *
 * Configured via {@code session.file} in application.properties.
 */
@Service
public class SessionStoreService {

    private static final Logger log              = LoggerFactory.getLogger(SessionStoreService.class);
    private static final long   SESSION_TTL_HOURS = 24L;

    private final ObjectMapper                                 mapper  = new ObjectMapper();
    private final ConcurrentHashMap<String, PersistedSession> cache   = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock                       rwLock  = new ReentrantReadWriteLock();
    private final ZerodhaSessionStoreService                   zerodhaStore;

    @Value("${session.file:session-store.json}")
    private String filePath;

    public SessionStoreService(@Lazy ZerodhaSessionStoreService zerodhaStore) {
        this.zerodhaStore = zerodhaStore;
    }

    @PostConstruct
    public void init() {
        loadFromDisk();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private void loadFromDisk() {
        File file = resolveFile();
        if (!file.exists()) {
            log.info("No session file at '{}'. Starting fresh.", file.getAbsolutePath());
            return;
        }
        try {
            PersistedSession[] sessions = mapper.readValue(file, PersistedSession[].class);
            int loaded = 0;
            for (PersistedSession s : sessions) {
                if (s.getClientcode() != null && !isExpired(s)) {
                    cache.put(s.getClientcode(), s);
                    loaded++;
                    log.info("Restored session for clientcode '{}' (logged in at {})",
                            s.getClientcode(), Instant.ofEpochMilli(s.getLoginTimeEpochMs()));
                    if (s.getZerodhaSessionData() != null
                            && s.getZerodhaSessionData().getAccessToken() != null) {
                        zerodhaStore.save(s.getZerodhaSessionData());
                        log.info("Restored Zerodha session for clientcode '{}'", s.getClientcode());
                    }
                }
            }
            log.info("Session store loaded: {}/{} sessions still valid.", loaded, sessions.length);
        } catch (Exception e) {
            log.warn("Could not read session file '{}': {} – starting fresh.", file.getAbsolutePath(), e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Save (or overwrite) an AngelOne session and flush to disk. */
    public void save(String clientcode, AngelLoginResponse response) {
        PersistedSession session = new PersistedSession();
        session.setClientcode(clientcode);
        session.setLoginTimeEpochMs(Instant.now().toEpochMilli());
        session.setLoginResponse(response);

        rwLock.writeLock().lock();
        try {
            cache.put(clientcode, session);
            flushToDisk();
        } finally {
            rwLock.writeLock().unlock();
        }
        log.info("AngelOne session saved for clientcode '{}'.", clientcode);
    }

    /** Attach a Zerodha session to an existing persisted session and flush to disk. */
    public void attachZerodhaSession(String clientcode, ZerodhaSessionData zerodha) {
        rwLock.writeLock().lock();
        try {
            PersistedSession session = cache.get(clientcode);
            if (session == null) {
                session = new PersistedSession();
                session.setClientcode(clientcode);
                session.setLoginTimeEpochMs(Instant.now().toEpochMilli());
            }
            session.setZerodhaSessionData(zerodha);
            cache.put(clientcode, session);
            flushToDisk();
        } finally {
            rwLock.writeLock().unlock();
        }
        log.info("Zerodha session attached to clientcode '{}'.", clientcode);
    }

    /**
     * Returns the stored {@link AngelLoginResponse} for a clientcode,
     * or {@code null} if none / expired.
     */
    public AngelLoginResponse get(String clientcode) {
        rwLock.readLock().lock();
        try {
            PersistedSession s = cache.get(clientcode);
            if (s == null) return null;
            if (isExpired(s)) {
                rwLock.readLock().unlock();
                evict(clientcode);
                rwLock.readLock().lock();
                return null;
            }
            return s.getLoginResponse();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Returns the clientcode of any currently valid session, or empty. */
    public Optional<String> getAnyActiveClientcode() {
        rwLock.readLock().lock();
        try {
            return cache.entrySet().stream()
                    .filter(e -> !isExpired(e.getValue()))
                    .map(java.util.Map.Entry::getKey)
                    .findFirst();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Returns all currently valid (non-expired) sessions. */
    public List<PersistedSession> getAllActive() {
        rwLock.readLock().lock();
        try {
            return cache.values().stream()
                    .filter(s -> !isExpired(s))
                    .collect(Collectors.toList());
        } finally {
            rwLock.readLock().unlock();
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void evict(String clientcode) {
        rwLock.writeLock().lock();
        try {
            cache.remove(clientcode);
            flushToDisk();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /** Atomic write: write to .tmp then rename. */
    private void flushToDisk() {
        File target = resolveFile();
        File tmp    = new File(target.getParent() == null ? "." : target.getParent(),
                               target.getName() + ".tmp");
        try {
            if (target.getParentFile() != null) target.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp, cache.values().toArray());
            if (target.exists()) target.delete();
            if (!tmp.renameTo(target)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(target, cache.values().toArray());
                tmp.delete();
            }
        } catch (Exception e) {
            log.error("Failed to persist sessions to '{}': {}", target.getAbsolutePath(), e.getMessage());
        }
    }

    private boolean isExpired(PersistedSession s) {
        return Duration.between(Instant.ofEpochMilli(s.getLoginTimeEpochMs()), Instant.now())
                       .toHours() >= SESSION_TTL_HOURS;
    }

    private File resolveFile() {
        File f = new File(filePath);
        return f.isAbsolute() ? f : new File(System.getProperty("user.dir"), filePath);
    }
}
