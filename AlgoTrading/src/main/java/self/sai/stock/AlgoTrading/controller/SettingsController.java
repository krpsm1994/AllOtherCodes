package self.sai.stock.AlgoTrading.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import self.sai.stock.AlgoTrading.entity.Setting;
import self.sai.stock.AlgoTrading.repository.SettingRepository;
import self.sai.stock.AlgoTrading.service.SettingService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingRepository settingRepository;
    private final SettingService    settingService;

    public SettingsController(SettingRepository settingRepository,
                              SettingService settingService) {
        this.settingRepository = settingRepository;
        this.settingService    = settingService;
    }

    /**
     * Upsert a single setting.
     * Body: { "group": "...", "key": "...", "value": "..." }
     */
    @PostMapping
    public ResponseEntity<?> saveSetting(@RequestBody Map<String, String> body) {
        String group = body.getOrDefault("group", "").trim();
        if (group.isEmpty()) group = "default";

        String key   = body.getOrDefault("key",   "").trim();
        String value = body.getOrDefault("value", "");

        if (key.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Missing key"));
        }

        try {
            Setting s = settingRepository.findByKeyAndGroupName(key, group).orElseGet(Setting::new);
            s.setGroupName(group);
            s.setKey(key);
            s.setValue(value);
            Setting saved = settingRepository.save(s);
            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    /**
     * Bulk upsert settings.
     * Body: [ { "group": "...", "key": "...", "value": "..." }, ... ]
     */
    @PostMapping("/bulk")
    public ResponseEntity<?> saveSettingsBulk(@RequestBody List<Map<String, String>> entries) {
        try {
            settingService.saveAll(entries);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    /**
     * Retrieve all settings grouped by group name, with defaults applied.
     * Response: { success: true, settings: { group -> { key -> value }, ... } }
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllSettings() {
        Map<String, Map<String, String>> grouped = settingService.getAllGrouped();
        return ResponseEntity.ok(Map.of("success", true, "settings", grouped));
    }

    /**
     * Retrieve matching values for the given key across all groups.
     * Response: { success: true, values: { group -> value, ... } }
     */
    @GetMapping
    public ResponseEntity<?> getSetting(@RequestParam String key) {
        List<Setting> matches = settingRepository.findAll().stream()
                .filter(s -> key.equals(s.getKey()))
                .toList();
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Not found"));
        }
        Map<String, String> map = matches.stream()
                .collect(Collectors.toMap(Setting::getGroupName, Setting::getValue));
        return ResponseEntity.ok(Map.of("success", true, "values", map));
    }
}
