package self.sai.stock.AlgoTrading.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.*;

/**
 * Fetches the AngelOne OpenAPIScripMaster instrument list, applies watchlist-based
 * filter rules read from {@code instruments.txt}, and replaces the entire
 * {@code instruments} table.
 *
 * <p>Filter rules:
 * <ol>
 *   <li>exchange == "NSE" (no NFO, no BSE, no MCX)</li>
 *   <li>symbol ends with "-EQ" (equity only — excludes index, ETF, bond tokens)</li>
 *   <li>name must appear in instruments.txt (either watchlist section)</li>
 *   <li>If a name is in both sections, "10MinWatchlist" wins as the stored type.</li>
 * </ol>
 */
@Service
public class InstrumentRefreshService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentRefreshService.class);

    private static final String SCRIP_MASTER_URL =
            "https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public InstrumentRefreshService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Refreshes the instruments table from the AngelOne ScripMaster.
     *
     * @return number of rows persisted
     */
    public int refresh() {
        log.info("Fetching ScripMaster from AngelOne…");
        List<ScripMasterEntry> all = fetchAll();
        log.info("Fetched {} raw entries from ScripMaster", all.size());

        // ── Load instruments.txt → nameToType map (10Min wins on duplicate) ───
        Map<String, String> nameToType = buildNameToTypeMap();
        log.info("instruments.txt: {} unique names loaded ({} from 10MinWatchlist, {} from DailyWatchlist)",
                nameToType.size(),
                nameToType.values().stream().filter("10MinWatchlist"::equals).count(),
                nameToType.values().stream().filter("DailyWatchlist"::equals).count());

        // ── Filter and build insert batch ─────────────────────────────────────
        // Use LinkedHashMap keyed by token to deduplicate (first NSE -EQ entry wins)
        Map<String, Object[]> rowMap = new LinkedHashMap<>();
        for (ScripMasterEntry e : all) {
            String name = e.getName() != null ? e.getName().trim() : "";
            if (name.isEmpty()) continue;

            // Rule 2: name must be in instruments.txt
            String type = nameToType.get(name);
            if (type == null) continue;

            // Rule 1: NSE only
            String exchange = e.getExchSeg() != null ? e.getExchSeg().trim() : "";
            if (!"NSE".equals(exchange)) continue;

            // Rule 1 (extra guard): symbol must end with "-EQ" → equities only
            String symbol = e.getSymbol() != null ? e.getSymbol().trim() : "";
            if (!symbol.endsWith("-EQ")) continue;

            String token = e.getToken() != null ? e.getToken().trim() : "";
            if (token.isEmpty()) continue;

            int lotSize = parseLotsize(e.getLotsize());

            // token is the natural key; first occurrence wins
            rowMap.putIfAbsent(token, new Object[]{token, name, exchange, lotSize, type});
        }

        List<Object[]> rows = new ArrayList<>(rowMap.values());
        log.info("Matched {} instruments to persist", rows.size());

        // Log which watchlist names had no match (useful for diagnosing typos)
        Set<String> matchedNames = new HashSet<>();
        rows.forEach(r -> matchedNames.add((String) r[1]));
        nameToType.keySet().stream()
                .filter(n -> !matchedNames.contains(n))
                .forEach(n -> log.warn("  No NSE equity match found for: {}", n));

        // ── TRUNCATE + chunked batch INSERT ───────────────────────────────────
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
     * instruments : POLYCAB,KAYNES,DIVISLAB,...
     *
     * type : DailyWatchlist
     * instruments : POWERINDIA,FORCEMOT,...
     * </pre>
     *
     * <p>Returns a map of instrument name → watchlist type.
     * If a name appears in both sections, "10MinWatchlist" takes priority.
     */
    private Map<String, String> buildNameToTypeMap() {
        // Parse into sections first so we can apply priority correctly
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
                        if (!name.isEmpty()) {
                            sections.get(currentType).add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to read instruments.txt: {}", e.getMessage(), e);
        }

        // Build nameToType: add DailyWatchlist first, then let 10MinWatchlist overwrite
        Map<String, String> nameToType = new LinkedHashMap<>();
        for (String name : sections.getOrDefault("DailyWatchlist", Collections.emptySet())) {
            nameToType.put(name, "DailyWatchlist");
        }
        for (String name : sections.getOrDefault("10MinWatchlist", Collections.emptySet())) {
            nameToType.put(name, "10MinWatchlist");   // overrides DailyWatchlist if duplicate
        }
        return nameToType;
    }

    private int parseLotsize(String s) {
        if (s == null || s.isBlank()) return 1;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 1; }
    }
}
