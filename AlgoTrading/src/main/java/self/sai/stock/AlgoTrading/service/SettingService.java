package self.sai.stock.AlgoTrading.service;

import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.entity.Setting;
import self.sai.stock.AlgoTrading.repository.SettingRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application-level settings backed by the {@code settings} table.
 * Settings are stored as group / key / value triples.
 *
 * <p>Defaults are always returned when a key has not yet been persisted,
 * so callers never receive {@code null} for a known key.
 */
@Service
public class SettingService {

    // ── Group constants ───────────────────────────────────────────────────────
    public static final String GRP_ALGO_SCAN = "Algo Scan";
    public static final String GRP_ORDERS    = "Orders";

    // ── Defaults ──────────────────────────────────────────────────────────────
    private static final Map<String, Map<String, String>> DEFAULTS;
    static {
        DEFAULTS = new LinkedHashMap<>();

        Map<String, String> algoScan = new LinkedHashMap<>();
        algoScan.put("numberOfCandles",        "15");
        DEFAULTS.put(GRP_ALGO_SCAN, algoScan);

        Map<String, String> orders = new LinkedHashMap<>();
        orders.put("place10MinOrders",         "true");
        orders.put("placeDailyOrders",         "true");
        orders.put("orderType",                "MARKET");
        orders.put("pollingIntervalMinutes",   "5");
        orders.put("max10MinOrders",           "5");
        orders.put("maxDailyOrders",           "5");
        DEFAULTS.put(GRP_ORDERS, orders);
    }

    private final SettingRepository settingRepository;

    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all settings grouped by group name.
     * Missing keys are pre-filled with their default values.
     *
     * @return {@code { group -> { key -> value } }}
     */
    public Map<String, Map<String, String>> getAllGrouped() {
        // Start with defaults, then overwrite with DB values
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        DEFAULTS.forEach((grp, keys) -> result.put(grp, new LinkedHashMap<>(keys)));

        settingRepository.findAll().forEach(s -> {
            if (s.getGroupName() != null && s.getKey() != null) {
                result.computeIfAbsent(s.getGroupName(), g -> new LinkedHashMap<>())
                      .put(s.getKey(), s.getValue() != null ? s.getValue() : "");
            }
        });
        return result;
    }

    /**
     * Bulk upsert settings. Each entry is a map with keys {@code group}, {@code key},
     * {@code value}. Existing rows are updated; new rows are inserted.
     */
    public void saveAll(List<Map<String, String>> entries) {
        for (Map<String, String> entry : entries) {
            String group = entry.getOrDefault("group", "default").trim();
            String key   = entry.getOrDefault("key",   "").trim();
            String value = entry.getOrDefault("value", "");
            if (key.isEmpty()) continue;

            Setting s = settingRepository.findByKeyAndGroupName(key, group).orElseGet(Setting::new);
            s.setGroupName(group);
            s.setKey(key);
            s.setValue(value);
            settingRepository.save(s);
        }
    }

    /**
     * Returns a single setting value, falling back to {@code defaultValue} if absent.
     */
    public String get(String group, String key, String defaultValue) {
        return settingRepository.findByKeyAndGroupName(key, group)
                .map(Setting::getValue)
                .orElseGet(() -> DEFAULTS.getOrDefault(group, Map.of()).getOrDefault(key, defaultValue));
    }

    /** Convenience — parses the value as {@code int}. */
    public int getInt(String group, String key, int defaultValue) {
        String v = get(group, key, String.valueOf(defaultValue));
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    /** Convenience — parses the value as {@code boolean}. */
    public boolean getBoolean(String group, String key, boolean defaultValue) {
        String v = get(group, key, String.valueOf(defaultValue));
        return "true".equalsIgnoreCase(v.trim());
    }
}
