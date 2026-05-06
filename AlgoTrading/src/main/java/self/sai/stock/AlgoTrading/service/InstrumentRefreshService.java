package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.ScripMasterEntry;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Fetches the AngelOne OpenAPIScripMaster instrument list, applies watchlist-based
 * filter rules read from {@code instruments.txt}, and replaces the entire
 * {@code instruments} table.
 *
 * <p>Filter rules:
 * <ol>
 *   <li>{@code 10MinWatchlist} stocks — NSE exchange, symbol ends with "-EQ",
 *       exact name match from instruments.txt.</li>
 *   <li>{@code Index} entries — NSE exchange, case-insensitive name match;
 *       stored as type "Index".</li>
 *   <li>NFO options for each listed index — only the nearest 4 expiry dates,
 *       stored as type "IndexOption" with NFO exchange.</li>
 * </ol>
 *
 * Scheduled to run every Tuesday at 15:45 IST (after market close) to refresh
 * option expiry chains.
 */
@Service
@EnableScheduling
public class InstrumentRefreshService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentRefreshService.class);

    private static final String SCRIP_MASTER_URL =
            "https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json";

    /** Number of upcoming expiry dates to retain for each index's option chain. */
    private static final int MAX_EXPIRIES = 4;

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final OptionSelectionService optionSelectionService;

    public InstrumentRefreshService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                                    OptionSelectionService optionSelectionService) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.optionSelectionService = optionSelectionService;
    }

    // ── Scheduled weekly refresh ──────────────────────────────────────────────

    /** Auto-refreshes every Tuesday at 15:45 IST (after market close). */
    @Scheduled(cron = "0 45 15 ? * TUE", zone = "Asia/Kolkata")
    public void scheduledRefresh() {
        log.info("[InstrumentRefreshService] Tuesday 15:45 scheduled refresh triggered");
        try {
            int saved = refresh();
            log.info("[InstrumentRefreshService] Scheduled refresh complete — {} rows saved", saved);
        } catch (Exception e) {
            log.error("[InstrumentRefreshService] Scheduled refresh failed: {}", e.getMessage(), e);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Refreshes the instruments table from the AngelOne ScripMaster.
     *
     * @return number of rows persisted
     */
    public int refresh() {
        // ── Step 1: Read instruments.txt first ────────────────────────────────
        Map<String, String> nameToType = buildNameToTypeMap();
        long indexCount = nameToType.values().stream().filter("Index"::equals).count();
        log.info("instruments.txt: {} names ({} from 10MinWatchlist, {} from Index)",
                nameToType.size(),
                nameToType.values().stream().filter("10MinWatchlist"::equals).count(),
                indexCount);

        if (nameToType.isEmpty()) {
            log.warn("instruments.txt is empty or unreadable — aborting refresh");
            return 0;
        }

        // Build the set of index display names (case-insensitive) and their
        // corresponding option scrip names (e.g. "NIFTY 50" → "NIFTY")
        Map<String, String> indexDisplayToScripName = new LinkedHashMap<>();
        nameToType.forEach((name, type) -> {
            if ("Index".equals(type)) {
                indexDisplayToScripName.put(name.toLowerCase(), OptionSelectionService.toOptionName(name));
            }
        });

        // ── Step 2: Fetch full ScripMaster ────────────────────────────────────
        log.info("Fetching ScripMaster from AngelOne…");
        List<ScripMasterEntry> all = fetchAll();
        log.info("Fetched {} raw entries from ScripMaster", all.size());

        // ── Step 3: Build rows — stocks (10MinWatchlist) ──────────────────────
        Map<String, Object[]> rowMap = new LinkedHashMap<>();
        for (ScripMasterEntry e : all) {
            String name     = e.getName()   != null ? e.getName().trim()   : "";
            String exchange = e.getExchSeg()!= null ? e.getExchSeg().trim(): "";
            String symbol   = e.getSymbol() != null ? e.getSymbol().trim() : "";
            String token    = e.getToken()  != null ? e.getToken().trim()  : "";
            if (name.isEmpty() || token.isEmpty()) continue;

            // 10MinWatchlist stocks: NSE, exact name match, must end with -EQ
            if ("NSE".equals(exchange) && symbol.endsWith("-EQ")) {
                String stockType = nameToType.get(name);
                if (stockType != null && !"Index".equals(stockType)) {
                    rowMap.putIfAbsent(token, new Object[]{token, name, exchange,
                            parseLotsize(e.getLotsize()), stockType});
                }
            }

            // Index instruments: NSE, symbol matches instruments.txt name (case-insensitive)
            // ScripMaster index: symbol="Nifty 50", name="NIFTY" — we match on symbol
            if ("NSE".equals(exchange) && indexDisplayToScripName.containsKey(symbol.toLowerCase())) {
                rowMap.putIfAbsent(token, new Object[]{token, symbol, exchange,
                        parseLotsize(e.getLotsize()), "Index"});
            }
        }

        // ── Step 4: Build rows — ALL NFO OPTIDX options for listed indexes ───────
        // Match: exch_seg=NFO, instrumenttype=OPTIDX, name matches index scrip name
        Set<String> listedScripNames = indexDisplayToScripName.values().stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        List<ScripMasterEntry> allOptions = new ArrayList<>();
        for (ScripMasterEntry e : all) {
            if (!"NFO".equals(e.getExchSeg())) continue;
            if (!"OPTIDX".equals(e.getInstrumenttype())) continue;
            String optName = e.getName() != null ? e.getName().trim().toUpperCase() : "";
            if (!listedScripNames.contains(optName)) continue;
            String token = e.getToken() != null ? e.getToken().trim() : "";
            if (token.isEmpty()) continue;
            allOptions.add(e);
            rowMap.putIfAbsent(token, new Object[]{
                    token, e.getSymbol(), "NFO",
                    parseLotsize(e.getLotsize()), "IndexOption"});
        }
        log.info("Options picked: {} rows for indexes {}", allOptions.size(), listedScripNames);

        // ── Step 5: Populate option cache ─────────────────────────────────────
        optionSelectionService.cacheOptions(allOptions);

        // ── Step 6: Diagnostics — log names with no match ─────────────────────
        List<Object[]> rows = new ArrayList<>(rowMap.values());
        log.info("Total instruments to persist: {} (stocks + index + options)", rows.size());

        Set<String> matchedNames = rows.stream()
                .map(r -> ((String) r[1]).toLowerCase())
                .collect(Collectors.toSet());
        nameToType.keySet().stream()
                .filter(n -> !matchedNames.contains(n.toLowerCase()))
                .forEach(n -> log.warn("  No match found for: '{}'", n));

        // ── Step 7: TRUNCATE + chunked batch INSERT ───────────────────────────
        jdbcTemplate.execute("TRUNCATE TABLE instruments");
        log.info("Instruments table truncated");

        final int CHUNK = 500;
        int inserted = 0;
        for (int i = 0; i < rows.size(); i += CHUNK) {
            List<Object[]> chunk = rows.subList(i, Math.min(i + CHUNK, rows.size()));
            jdbcTemplate.batchUpdate(
                    "INSERT INTO instruments (token, `name`, `exchange`, lot_size, `type`) VALUES (?, ?, ?, ?, ?)",
                    chunk);
            inserted += chunk.size();
            log.info("  Inserted {}/{}", inserted, rows.size());
        }

        log.info("Instruments refresh complete — {} rows saved", inserted);
        return inserted;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches the full ScripMaster JSON (~30 MB) from AngelOne's CDN and
     * deserialises it into a list of {@link ScripMasterEntry}.
     */
    private List<ScripMasterEntry> fetchAll() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SCRIP_MASTER_URL))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("ScripMaster HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch ScripMaster: " + e.getMessage(), e);
        }
    }

    /**
     * Parses {@code instruments.txt} from the classpath.
     *
     * <p>Expected format:
     * <pre>
     * type : 10MinWatchlist
     * instruments : ADANIENT,TATAMOTORS,VEDL
     *
     * type : Index
     * instruments : NIFTY 50
     * </pre>
     *
     * <p>Returns a map of instrument name → watchlist type.
     * If a name appears in both sections, "10MinWatchlist" takes priority.
     */
    private Map<String, String> buildNameToTypeMap() {
        Map<String, Set<String>> sections = new LinkedHashMap<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("instruments.txt");
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String currentType = null;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("type :")) {
                    currentType = line.substring("type :".length()).trim();
                    sections.putIfAbsent(currentType, new LinkedHashSet<>());
                } else if (line.startsWith("instruments :") && currentType != null) {
                    String namesPart = line.substring("instruments :".length()).trim();
                    for (String raw : namesPart.split(",")) {
                        String name = raw.trim();
                        if (!name.isEmpty()) sections.get(currentType).add(name);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read instruments.txt: {}", e.getMessage(), e);
        }

        // Index first, then 10MinWatchlist overwrites on duplicate
        Map<String, String> nameToType = new LinkedHashMap<>();
        for (String name : sections.getOrDefault("Index", Collections.emptySet())) {
            nameToType.put(name, "Index");
        }
        for (String name : sections.getOrDefault("10MinWatchlist", Collections.emptySet())) {
            nameToType.put(name, "10MinWatchlist");
        }
        return nameToType;
    }

    private int parseLotsize(String s) {
        if (s == null || s.isBlank()) return 1;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 1; }
    }
}

