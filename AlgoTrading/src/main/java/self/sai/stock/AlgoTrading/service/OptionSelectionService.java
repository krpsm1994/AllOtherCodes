package self.sai.stock.AlgoTrading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.ScripMasterEntry;
import self.sai.stock.AlgoTrading.repository.InstrumentRepository;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Caches NFO index option entries from the AngelOne ScripMaster and provides
 * ATM option selection for a given index, option type (CE/PE), and current price.
 *
 * <p>Populated by {@link InstrumentRefreshService#refresh()} each time instruments
 * are refreshed.
 */
@Service
public class OptionSelectionService {

    private static final Logger log = LoggerFactory.getLogger(OptionSelectionService.class);

    /** AngelOne ScripMaster expiry format: "13MAR2025" or "13mar2025" — parsed case-insensitively. */
    private static final DateTimeFormatter EXPIRY_FMT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyyyy")
            .toFormatter(Locale.ENGLISH);

    /**
     * Matches AngelOne NFO option symbol format: &lt;NAME&gt;&lt;DDMMMYY&gt;&lt;STRIKE&gt;&lt;CE|PE&gt;
     * e.g. NIFTY26MAY2620400CE  →  group(1)="20400"
     *      BANKNIFTY26MAY2645000PE  →  group(1)="45000"
     */
    private static final Pattern STRIKE_PATTERN =
            Pattern.compile("^[A-Z]+\\d{2}[A-Z]{3}\\d{2}(\\d+(?:\\.\\d+)?)(CE|PE)$");

    /**
     * Full symbol parse: group(1)=name, group(2)=DD, group(3)=MMM, group(4)=YY, group(5)=strike, group(6)=CE|PE
     * e.g. NIFTY26MAY2620400CE → name=NIFTY, DD=26, MMM=MAY, YY=26, strike=20400, type=CE
     */
    private static final Pattern SYMBOL_PARSE_PATTERN =
            Pattern.compile("^([A-Z]+)(\\d{2})([A-Z]{3})(\\d{2})(\\d+(?:\\.\\d+)?)(CE|PE)$");

    /** In-memory cache of NFO OPTIDX entries. Replaced on each refresh. */
    private volatile List<ScripMasterEntry> nfoOptions = Collections.emptyList();

    @Autowired
    private InstrumentRepository instrumentRepository;

    // ── Cache population ──────────────────────────────────────────────────────

    // ── Startup load from DB ──────────────────────────────────────────────────

    /**
     * On startup, reconstructs ScripMasterEntry objects from the DB instruments table
     * (type=IndexOption) so the cache is ready without waiting for a ScripMaster refresh.
     * Name and expiry are derived by parsing the symbol string stored in the name column.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadFromDb() {
        List<self.sai.stock.AlgoTrading.entity.Instrument> dbOptions =
                instrumentRepository.findByType("IndexOption");
        if (dbOptions.isEmpty()) {
            log.warn("[OptionSelectionService] No IndexOption instruments in DB — cache will be empty until refresh");
            return;
        }
        List<ScripMasterEntry> entries = new ArrayList<>();
        int skipped = 0;
        for (self.sai.stock.AlgoTrading.entity.Instrument inst : dbOptions) {
            String symbol = inst.getName(); // e.g. NIFTY26MAY2620400CE
            if (symbol == null) { skipped++; continue; }
            Matcher m = SYMBOL_PARSE_PATTERN.matcher(symbol.toUpperCase());
            if (!m.matches()) {
                log.debug("[OptionSelectionService] Cannot parse symbol '{}' — skipping", symbol);
                skipped++;
                continue;
            }
            String indexName = m.group(1);           // e.g. NIFTY
            String dd        = m.group(2);           // e.g. 26
            String mmm       = m.group(3);           // e.g. MAY
            String yy        = m.group(4);           // e.g. 26
            String strike    = m.group(5);           // e.g. 20400
            // Reconstruct full 4-digit-year expiry: "26MAY2026"
            String expiry = dd + mmm + "20" + yy;

            ScripMasterEntry e = new ScripMasterEntry();
            e.setToken(inst.getToken());
            e.setSymbol(symbol);
            e.setName(indexName);
            e.setExpiry(expiry);
            e.setStrike(strike);
            e.setLotsize(String.valueOf(inst.getLotSize()));
            e.setInstrumenttype("OPTIDX");
            e.setExchSeg("NFO");
            entries.add(e);
        }
        this.nfoOptions = Collections.unmodifiableList(entries);
        log.info("[OptionSelectionService] Loaded {} NFO options from DB on startup ({} skipped)",
                entries.size(), skipped);
    }

    /**
     * Called by {@link InstrumentRefreshService} after downloading the full ScripMaster.
     * Filters to NFO OPTIDX entries and stores them in memory.
     */
    public void cacheOptions(List<ScripMasterEntry> allEntries) {
        List<ScripMasterEntry> opts = allEntries.stream()
                .filter(e -> "NFO".equals(e.getExchSeg()))
                .filter(e -> "OPTIDX".equals(e.getInstrumenttype()))
                .collect(Collectors.toList());
        this.nfoOptions = Collections.unmodifiableList(opts);
        log.info("OptionSelectionService cached {} NFO index option entries", opts.size());
    }

    // ── Option selection ──────────────────────────────────────────────────────

    /**
     * Selects the nearest ATM CE or PE option for the given index.
     *
     * <p>Rules:
     * <ol>
     *   <li>Option name (ScripMaster {@code name} field) must match {@code optionScripName}
     *       case-insensitively (e.g. "NIFTY").</li>
     *   <li>Symbol must contain "CE" or "PE" matching {@code optionType}.</li>
     *   <li>Expiry must be at least {@code minDaysToExpiry} calendar days from today.</li>
     *   <li>Among qualifying entries, pick the nearest expiry.</li>
     *   <li>Within that expiry, pick the strike closest to {@code currentPrice}.</li>
     * </ol>
     *
     * @param optionScripName  ScripMaster options name for the index (e.g. "NIFTY", "BANKNIFTY")
     * @param optionType       "CE" or "PE"
     * @param currentPrice     current index LTP
     * @param minDaysToExpiry  minimum calendar days until expiry (typically 3)
     * @return nearest ATM option entry, or empty if none found
     */
    public Optional<ScripMasterEntry> selectOption(String optionScripName,
                                                   String optionType,
                                                   double currentPrice,
                                                   int minDaysToExpiry) {
        LocalDate today      = LocalDate.now();
        LocalDate minExpiry  = today.plusDays(minDaysToExpiry);
        log.info("[OptionSelect] START optionScripName='{}' optionType='{}' currentPrice={} minDaysToExpiry={} today={} minExpiry={}",
                optionScripName, optionType, currentPrice, minDaysToExpiry, today, minExpiry);
        log.info("[OptionSelect] Total NFO options in cache: {}", nfoOptions.size());

        // Step 1: Filter by name
        List<ScripMasterEntry> byName = nfoOptions.stream()
                .filter(e -> e.getName() != null && e.getName().equalsIgnoreCase(optionScripName))
                .collect(Collectors.toList());
        log.info("[OptionSelect] After name filter ('{}') : {} entries", optionScripName, byName.size());

        // Step 2: Filter by option type (CE/PE)
        List<ScripMasterEntry> byType = byName.stream()
                .filter(e -> {
                    String sym = e.getSymbol() != null ? e.getSymbol().toUpperCase() : "";
                    return "CE".equals(optionType) ? sym.endsWith("CE") : sym.endsWith("PE");
                })
                .collect(Collectors.toList());
        log.info("[OptionSelect] After type filter ('{}') : {} entries", optionType, byType.size());

        // Step 3: Log all unique available expiry dates before the minExpiry filter
        List<String> allExpiries = byType.stream()
                .map(ScripMasterEntry::getExpiry)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        log.info("[OptionSelect] All available expiries for {} {}: {}", optionScripName, optionType, allExpiries);

        // Step 4: Filter by minExpiry
        List<ScripMasterEntry> candidates = byType.stream()
                .filter(e -> {
                    LocalDate expiry = parseExpiry(e.getExpiry());
                    boolean ok = expiry != null && !expiry.isBefore(minExpiry);
                    if (!ok) {
                        log.debug("[OptionSelect] Skipping symbol={} expiry='{}' parsed={} (before minExpiry={})",
                                e.getSymbol(), e.getExpiry(), expiry, minExpiry);
                    }
                    return ok;
                })
                .collect(Collectors.toList());

        // Log expiries that passed the filter
        List<String> validExpiries = candidates.stream()
                .map(ScripMasterEntry::getExpiry)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        log.info("[OptionSelect] Expiries passing minExpiry filter: {}", validExpiries);
        log.info("[OptionSelect] Candidate count after expiry filter: {}", candidates.size());

        if (candidates.isEmpty()) {
            log.warn("[OptionSelect] No {} options found for '{}' with expiry >= {} (today+{})",
                    optionType, optionScripName, minExpiry, minDaysToExpiry);
            return Optional.empty();
        }

        // Nearest expiry among candidates
        LocalDate nearestExpiry = candidates.stream()
                .map(e -> parseExpiry(e.getExpiry()))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        log.info("[OptionSelect] Nearest expiry selected: {}", nearestExpiry);
        if (nearestExpiry == null) return Optional.empty();

        // Log all strikes available at nearest expiry
        List<String> strikesAtExpiry = candidates.stream()
                .filter(e -> nearestExpiry.equals(parseExpiry(e.getExpiry())))
                .map(e -> {
                    double s = parseStrikeFromSymbol(e.getSymbol());
                    return e.getSymbol() + " (strike=" + s + " diff=" + Math.abs(s - currentPrice) + ")";
                })
                .sorted()
                .collect(Collectors.toList());
        log.info("[OptionSelect] Strikes at nearest expiry {} : {}", nearestExpiry, strikesAtExpiry);

        // Nearest ATM strike within that expiry
        Optional<ScripMasterEntry> selected = candidates.stream()
                .filter(e -> nearestExpiry.equals(parseExpiry(e.getExpiry())))
                .min(Comparator.comparingDouble(e -> Math.abs(parseStrikeFromSymbol(e.getSymbol()) - currentPrice)));

        selected.ifPresentOrElse(
                e -> log.info("[OptionSelect] SELECTED symbol={} token={} strike={} expiry={} (currentPrice={})",
                        e.getSymbol(), e.getToken(), parseStrikeFromSymbol(e.getSymbol()), e.getExpiry(), currentPrice),
                () -> log.warn("[OptionSelect] No ATM strike selected after filtering")
        );
        return selected;
    }

    /**
     * Looks up a specific option entry by its token from the cache.
     * Used to retrieve the expiry date of an active option trade.
     */
    public Optional<ScripMasterEntry> findByToken(String token) {
        if (token == null) return Optional.empty();
        return nfoOptions.stream()
                .filter(e -> token.equals(e.getToken()))
                .findFirst();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts an instruments.txt index display name (e.g. "NIFTY 50") to the ScripMaster
     * option entry name (e.g. "NIFTY"). Strips numeric words and collapses spaces.
     *
     * <p>Examples:
     * <ul>
     *   <li>"NIFTY 50"    → "NIFTY"</li>
     *   <li>"BANK NIFTY"  → "BANKNIFTY"</li>
     * </ul>
     */
    public static String toOptionName(String indexDisplayName) {
        if (indexDisplayName == null) return "";
        return Arrays.stream(indexDisplayName.trim().split("\\s+"))
                .filter(w -> !w.matches("\\d+"))
                .collect(Collectors.joining())
                .toUpperCase();
    }

    /** Parses an expiry string in the format "13MAR2025". Returns null on failure. */
    public LocalDate parseExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) return null;
        try {
            return LocalDate.parse(expiry.trim().toUpperCase(), EXPIRY_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Converts an AngelOne ScripMaster option symbol to the Zerodha Kite NFO
     * trading symbol format.
     *
     * <p>AngelOne may include a decimal in the strike
     * (e.g. {@code NIFTY26MAY2620400.00CE}).
     * Kite requires the strike as a plain integer with no decimal
     * (e.g. {@code NIFTY26MAY2620400CE}).
     *
     * <p>If the symbol cannot be parsed it is returned unchanged.
     *
     * @param angelOneSymbol raw symbol from AngelOne ScripMaster
     * @return Kite-compatible NFO trading symbol
     */
    public static String toKiteSymbol(String angelOneSymbol) {
        if (angelOneSymbol == null) return "";
        String upper = angelOneSymbol.trim().toUpperCase();
        Matcher m = SYMBOL_PARSE_PATTERN.matcher(upper);
        if (!m.matches()) return upper;
        String name      = m.group(1);   // e.g. NIFTY
        String dd        = m.group(2);   // e.g. 26 (day, always 2 digits)
        String mmm       = m.group(3);   // e.g. MAY
        String yy        = m.group(4);   // e.g. 26 (2-digit year)
        String strikeRaw = m.group(5);   // e.g. 20400.00 or 20400
        String type      = m.group(6);   // CE or PE
        // Month number: single digit for Jan–Sep (1–9), two digits for Oct–Dec (10–12)
        // Zerodha format: NIFTY2651224100CE = NIFTY + 25 + 5 + 12 + 24100 + CE
        int monthNum = Month.valueOf(mmm).getValue();
        String monthStr = String.valueOf(monthNum); // naturally single-digit for 1-9
        // Kite requires integer strike — drop any decimal part
        long strikeInt = (long) Double.parseDouble(strikeRaw);
        // Kite NFO format: {NAME}{YY}{M}{DD}{STRIKE}{TYPE}
        String kiteSymbol = name + yy + monthStr + dd + strikeInt + type;
        LoggerFactory.getLogger(OptionSelectionService.class)
                .info("[toKiteSymbol] '{}' → '{}'", angelOneSymbol, kiteSymbol);
        return kiteSymbol;
    }

    /**
     * Extracts the strike price from an NFO option symbol.
     * Symbol format: &lt;NAME&gt;&lt;DDMMMYY&gt;&lt;STRIKE&gt;&lt;CE|PE&gt;
     * e.g. "NIFTY26MAY2620400CE" → 20400.0
     * This avoids relying on the ScripMaster {@code strike} field (not persisted in DB).
     */
    public double parseStrikeFromSymbol(String symbol) {
        if (symbol == null) return 0.0;
        Matcher m = STRIKE_PATTERN.matcher(symbol.trim().toUpperCase());
        if (m.matches()) {
            try { return Double.parseDouble(m.group(1)); } catch (NumberFormatException e) { return 0.0; }
        }
        log.warn("Could not parse strike from symbol '{}'", symbol);
        return 0.0;
    }
}
