package self.sai.stock.AlgoTrading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.CandleBar;
import self.sai.stock.AlgoTrading.dto.ScripMasterEntry;
import self.sai.stock.AlgoTrading.dto.TEMASeries;
import self.sai.stock.AlgoTrading.entity.Instrument;
import self.sai.stock.AlgoTrading.entity.Trade;
import self.sai.stock.AlgoTrading.repository.InstrumentRepository;
import self.sai.stock.AlgoTrading.repository.TradeRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs TEMA-based scan on index (NIFTY 50, etc.) 5-minute candles and
 * creates CE/PE option trades on signals.
 *
 * <p>Signal mapping:
 * <ul>
 *   <li>BUY  signal → buy CE option; close any active PE option</li>
 *   <li>SELL signal → buy PE option; close any active CE option</li>
 * </ul>
 *
 * <p>If today is the expiry date of an active option, only that option is
 * closed — no new option is opened.
 */
@Service
public class IndexOptionScanService {

    private static final Logger log = LoggerFactory.getLogger(IndexOptionScanService.class);

    private static final List<String> ACTIVE_STATUSES =
            List.of("WATCHING", "BUY PLACED", "OPEN", "SELL PLACED");

    private final InstrumentRepository  instrumentRepository;
    private final TradeRepository       tradeRepository;
    private final TradeMonitorService   tradeMonitor;
    private final OptionSelectionService optionSelectionService;
    private final AlgoScanService       algoScanService;

    public IndexOptionScanService(InstrumentRepository instrumentRepository,
                                  TradeRepository tradeRepository,
                                  TradeMonitorService tradeMonitor,
                                  OptionSelectionService optionSelectionService,
                                  AlgoScanService algoScanService) {
        this.instrumentRepository  = instrumentRepository;
        this.tradeRepository       = tradeRepository;
        this.tradeMonitor          = tradeMonitor;
        this.optionSelectionService = optionSelectionService;
        this.algoScanService       = algoScanService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scans each index token in {@code candleMap} with the TEMA algorithm.
     * On a buy or sell signal, creates option trades and places orders immediately.
     *
     * @param candleMap      token → 5-min candle list for each Index instrument
     * @param numberOfCandles TEMA grouping width (from settings)
     */
    public void runIndexScan(Map<String, List<CandleBar>> candleMap, int numberOfCandles) {
        log.info("Index option scan started — {} index token(s)", candleMap.size());
        for (Map.Entry<String, List<CandleBar>> entry : candleMap.entrySet()) {
            String         indexToken = entry.getKey();
            List<CandleBar> candles   = entry.getValue();

            Instrument inst = instrumentRepository.findById(indexToken).orElse(null);
            if (inst == null) {
                log.warn("Index instrument not found for token {}", indexToken);
                continue;
            }

            if (candles == null || candles.size() < numberOfCandles * 2 + 1) {
                log.warn("Not enough candles for index {} (have {})", inst.getName(),
                        candles == null ? 0 : candles.size());
                continue;
            }

            // Skip if last raw candle is stale — prevents acting on 9:15 candle at 10:25
            CandleBar lastCandle = candles.get(candles.size() - 1);
            LocalDateTime staleThreshold = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30);
            if (lastCandle.getTimestamp().isBefore(staleThreshold)) {
                log.info("[IndexScan] Skipping {} — last candle {} is stale (threshold {})",
                        inst.getName(), lastCandle.getTimestamp(), staleThreshold);
                continue;
            }

            // TEMA computation — reuse AlgoScanService helpers
            List<TEMASeries> htfSeries    = algoScanService.generateAltSeries(candles, 2);
            List<TEMASeries> signalSeries = algoScanService.generateSignalSeries(htfSeries, numberOfCandles);

            if (signalSeries.size() < 2) {
                log.warn("Not enough signal candles for {}", inst.getName());
                continue;
            }

            TEMASeries signal = signalSeries.get(signalSeries.size() - 1);
            TEMASeries prev   = signalSeries.get(signalSeries.size() - 2);

            log.info("Index {} — prev={} signal={}", inst.getName(), prev, signal);

            // Signal must be for today
            if (!signal.getDate().toLocalDate().equals(LocalDate.now())) {
                log.info("Index {} signal date {} != today, skipping", inst.getName(), signal.getDate().toLocalDate());
                continue;
            }

            double  currentPrice = signal.getPrice();
            boolean expiryToday  = isExpiryToday(indexToken);

            if (prev.getCloseAlt() < prev.getOpenAlt() && signal.getCloseAlt() > signal.getOpenAlt()) {
                // ── BUY signal: buy CE, close PE ─────────────────────────────
                log.info("Index BUY signal for {} price={}", inst.getName(), currentPrice);
                closePeIfActive(indexToken);
                if (!expiryToday) {
                    openOption(indexToken, inst, "CE", currentPrice, signal.getDate().toString());
                } else {
                    log.info("Expiry today for {} — not opening new CE", inst.getName());
                }

            } else if (prev.getCloseAlt() > prev.getOpenAlt() && signal.getCloseAlt() < signal.getOpenAlt()) {
                // ── SELL signal: buy PE, close CE ─────────────────────────────
                log.info("Index SELL signal for {} price={}", inst.getName(), currentPrice);
                closeCeIfActive(indexToken);
                if (!expiryToday) {
                    openOption(indexToken, inst, "PE", currentPrice, signal.getDate().toString());
                } else {
                    log.info("Expiry today for {} — not opening new PE", inst.getName());
                }
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns true if any active option trade for this index expires today. */
    private boolean isExpiryToday(String indexToken) {
        LocalDate today = LocalDate.now();
        return tradeRepository.findByStatusIn(ACTIVE_STATUSES).stream()
                .filter(t -> "IndexOption".equals(t.getType()))
                .filter(t -> indexToken.equals(t.getIndexToken()))
                .anyMatch(t -> optionSelectionService.findByToken(t.getToken())
                        .map(e -> today.equals(optionSelectionService.parseExpiry(e.getExpiry())))
                        .orElse(false));
    }

    private void closeCeIfActive(String indexToken) {
        findActiveTrade(indexToken, "CE").ifPresent(t -> {
            log.info("Closing active CE trade id={} name={}", t.getId(), t.getName());
            t.setSellPrice(0);
            tradeRepository.save(t);
            tradeMonitor.onSellTriggered(t.getId());
        });
    }

    private void closePeIfActive(String indexToken) {
        findActiveTrade(indexToken, "PE").ifPresent(t -> {
            log.info("Closing active PE trade id={} name={}", t.getId(), t.getName());
            t.setSellPrice(0);
            tradeRepository.save(t);
            tradeMonitor.onSellTriggered(t.getId());
        });
    }

    private Optional<Trade> findActiveTrade(String indexToken, String optionType) {
        return tradeRepository.findByStatusIn(ACTIVE_STATUSES).stream()
                .filter(t -> "IndexOption".equals(t.getType()))
                .filter(t -> indexToken.equals(t.getIndexToken()))
                .filter(t -> optionType.equals(t.getOptionType()))
                .findFirst();
    }

    /**
     * Selects the nearest ATM option, creates a WATCHING trade, and immediately
     * places a market buy order via {@link TradeMonitorService#placeManualBuyOrder(Trade)}.
     * This bypasses the tick-triggered WATCHING path so the order is placed instantly.
     */
    private void openOption(String indexToken, Instrument indexInst,
                            String optionType, double currentPrice, String date) {

        // Do not open if an active trade already exists for this option type
        if (findActiveTrade(indexToken, optionType).isPresent()) {
            log.info("Active {} trade for {} already exists — skipping", optionType, indexInst.getName());
            return;
        }

        String optionScripName = OptionSelectionService.toOptionName(indexInst.getName());
        log.info("[openOption] indexName='{}' -> optionScripName='{}' optionType='{}' currentPrice={}",
                indexInst.getName(), optionScripName, optionType, currentPrice);
        Optional<ScripMasterEntry> optEntry =
                optionSelectionService.selectOption(optionScripName, optionType, currentPrice, 3);

        if (optEntry.isEmpty()) {
            log.warn("[openOption] No {} option found for {} (optionScripName={})", optionType, indexInst.getName(), optionScripName);
            return;
        }

        ScripMasterEntry opt     = optEntry.get();
        int              lotSize = indexInst.getLotSize();

        Trade trade = new Trade();
        trade.setToken(opt.getToken());           // NFO option token
        trade.setName(OptionSelectionService.toKiteSymbol(opt.getSymbol())); // Kite-format symbol, e.g. NIFTY26MAY2620400CE
        trade.setType("IndexOption");
        trade.setOptionType(optionType);          // "CE" or "PE"
        trade.setIndexToken(indexToken);          // parent NSE index token
        trade.setDate(date);
        trade.setBuyPrice(0);                     // 0 until fill — updated by onBuyFilled after order confirmation
        trade.setStatus("WATCHING");
        trade.setNoOfShares(lotSize);             // 1 lot
        trade.setExchange("NFO");
        tradeRepository.save(trade);

        log.info("Created {} option trade id={} symbol={} strike={} expiry={} qty={}",
                optionType, trade.getId(), opt.getSymbol(),
                opt.getStrike(), opt.getExpiry(), lotSize);

        // Place buy immediately at market (no tick subscription needed)
        tradeMonitor.placeManualBuyOrder(trade);
    }
}
