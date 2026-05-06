package self.sai.stock.AlgoTrading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import self.sai.stock.AlgoTrading.dto.LiveTick;
import self.sai.stock.AlgoTrading.entity.Trade;
import self.sai.stock.AlgoTrading.repository.TradeRepository;
import self.sai.stock.AlgoTrading.service.SettingService;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/**
 * Generic trade monitor that works for both 10-minute and daily timeframe scans.
 *
 * <h3>Lifecycle of a trade</h3>
 * <pre>
 *  WATCHING    ─── any tick arrives          → place MARKET buy   → BUY PLACED
 *
 *  BUY PLACED  ─── order FILLED              → OPEN  (buyPrice updated to fill price)
 *              ─── order REJECTED             → "Broker Rejected: [reason]"
 *              ─── EOD (≥15:30)               → OPEN  (retry next day)
 *
 *  OPEN        ─── tick arrives              → PnL recalculated
 *              ─── algo scan sets sellPrice  → (onSellTriggered) → SELL PLACED
 *
 *  SELL PLACED ─── order FILLED              → CLOSED (PnL finalised, removed from cache)
 *              ─── order REJECTED             → OPEN   (retry)
 *              ─── EOD (≥15:30)               → OPEN   (retry next day)
 * </pre>
 *
 * <p>Active trades are loaded from the DB on stream start and kept in memory.
 * All changes are flushed to the DB every 5 minutes and on stream stop.
 * A dedicated order-polling thread checks pending orders every 5 seconds independently
 * of the tick stream.
 */
@Service
@EnableScheduling
public class TradeMonitorService {

    private static final Logger log = LoggerFactory.getLogger(TradeMonitorService.class);

    private static final List<String> ACTIVE_STATUSES =
            List.of("WATCHING", "BUY PLACED", "SELL PLACED", "OPEN");

    private static final LocalTime EOD_TIME = LocalTime.of(15, 30);
    private static final ZoneId    IST       = ZoneId.of("Asia/Kolkata");

    /** Poll interval in ms — refreshed from settings on each stream start. */
    private volatile long pollIntervalMs = 300_000L; // 5-min default

    // ── In-memory state ───────────────────────────────────────────────────────

    /** token → list of active trades (used in tick processing). */
    private final ConcurrentHashMap<String, List<Trade>> activeByToken = new ConcurrentHashMap<>();

    /** tradeId → trade (used by sell-trigger and order poller). */
    private final ConcurrentHashMap<Long, Trade> activeById = new ConcurrentHashMap<>();

    /** tradeId → epoch-ms of last order-status poll (throttle). */
    private final ConcurrentHashMap<Long, Long> lastPollMs = new ConcurrentHashMap<>();

    private ScheduledExecutorService orderPoller;

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final TradeRepository    tradeRepository;
    private final BrokerOrderService brokerOrderService;
    private final SettingService     settingService;
    private final TradeSseBroadcaster tradeSseBroadcaster;

    public TradeMonitorService(TradeRepository tradeRepository,
                               BrokerOrderService brokerOrderService,
                               SettingService settingService,
                               TradeSseBroadcaster tradeSseBroadcaster) {
        this.tradeRepository    = tradeRepository;
        this.brokerOrderService = brokerOrderService;
        this.settingService     = settingService;
        this.tradeSseBroadcaster = tradeSseBroadcaster;
    }

    // ── Stream lifecycle ──────────────────────────────────────────────────────

    /**
     * Called when the WebSocket stream opens. Loads all active trades from the DB
     * and starts the independent order-poll thread.
     */
    public synchronized void onStreamStart() {
        pollIntervalMs = settingService.getInt(SettingService.GRP_ORDERS, "pollingIntervalMinutes", 5) * 60_000L;
        activeByToken.clear();
        activeById.clear();
        lastPollMs.clear();
        loadActiveTrades();
        startOrderPoller();
        log.info("TradeMonitor started — {} active trade(s) across {} token(s), pollInterval={}ms",
                activeById.size(), activeByToken.size(), pollIntervalMs);
    }

    /**
     * Called when the WebSocket stream closes. Flushes all in-memory trade state to
     * the DB and stops the order-poll thread.
     */
    public synchronized void onStreamStop() {
        stopOrderPoller();
        flushToDb();
        activeByToken.clear();
        activeById.clear();
        lastPollMs.clear();
        log.info("TradeMonitor stopped");
    }

    // ── Tick processing ───────────────────────────────────────────────────────

    /**
     * Called on every live tick. Evaluates all active trades for the tick's token.
     */
    public void onTick(LiveTick tick) {
        List<Trade> trades = activeByToken.get(tick.getSymboltoken());
        if (trades == null || trades.isEmpty()) return;

        double ltp = tick.getLtp();
        List<Trade> toEvict = new ArrayList<>();

        for (Trade trade : trades) {
            boolean evict = evaluateTick(trade, ltp);
            if (evict) toEvict.add(trade);
        }

        toEvict.forEach(t -> {
            trades.remove(t);
            activeById.remove(t.getId());
            lastPollMs.remove(t.getId());
            log.debug("TradeMonitor evicted trade id={} status={}", t.getId(), t.getStatus());
        });
    }

    // ── Sell trigger (called by AlgoScanService) ──────────────────────────────

    /**
     * Called by the algo scan when a sell signal fires for an existing OPEN trade.
     * Refreshes the trade from DB (to get the latest sellPrice set by the scan),
     * places a MARKET SELL order, and hands it off to polling.
     *
     * @param tradeId  ID of the trade that should be sold.
     */
    public void onSellTriggered(Long tradeId) {
        // Always refresh from DB to get the sell price set by the scan
        Trade fresh = tradeRepository.findById(tradeId).orElse(null);
        if (fresh == null) {
            log.warn("onSellTriggered: trade {} not found", tradeId);
            return;
        }
        if (!"OPEN".equals(fresh.getStatus())) {
            log.warn("onSellTriggered: trade {} status is '{}', skipping", tradeId, fresh.getStatus());
            return;
        }

        // Sync the refreshed sell price into the cached trade (if present)
        Trade trade = activeById.getOrDefault(tradeId, fresh);
        trade.setSellPrice(fresh.getSellPrice());
        trade.setStatus("SELL PLACED");
        tradeRepository.save(trade);
        tradeSseBroadcaster.broadcastUpdate(trade);

        // Ensure it's in the cache (may have been absent if stream was down when OPEN)
        addToCache(trade);

        log.info("TradeMonitor sell triggered — trade id={} name={} sellPrice={}",
                trade.getId(), trade.getName(), trade.getSellPrice());

        String orderType = settingService.get(SettingService.GRP_ORDERS, "orderType", "MARKET");
        BrokerOrderService.OrderResult result = brokerOrderService.placeSellOrder(trade, orderType, trade.getSellPrice());
        trade.setSellOrderId(result.orderId);

        if (result.status == BrokerOrderService.OrderStatus.FILLED) {
            onSellFilled(trade, result.fillPrice > 0 ? result.fillPrice : trade.getSellPrice());
            evictTrade(trade);
        } else if (result.status == BrokerOrderService.OrderStatus.REJECTED) {
            trade.setStatus("OPEN");
            trade.setSellOrderId(null);
            log.error("TradeMonitor sell order REJECTED for trade {} — reverting to OPEN. Reason: {}",
                    trade.getId(), result.rejectionReason);
        }
        // PENDING → stays SELL PLACED, polled by order-poller thread
        tradeRepository.save(trade);
    }

    // ── Periodic DB flush (every 5 minutes) ───────────────────────────────────

    @Scheduled(fixedDelay = 300_000)
    public void flushToDb() {
        Collection<Trade> trades = activeById.values();
        if (trades.isEmpty()) return;
        tradeRepository.saveAll(trades);
        log.debug("TradeMonitor flushed {} trade(s) to DB", trades.size());
    }

    // ── Rule engine ───────────────────────────────────────────────────────────

    /**
     * Evaluates a single trade against the current LTP.
     *
     * @return {@code true} if the trade should be evicted from the cache.
     */
    private boolean evaluateTick(Trade trade, double ltp) {
        return switch (trade.getStatus()) {
            case "WATCHING"    -> handleWatching(trade, ltp);
            case "BUY PLACED"  -> handleBuyPlaced(trade);
            case "OPEN"        -> { handleOpen(trade, ltp); yield false; }
            case "SELL PLACED" -> handleSellPlaced(trade);
            default            -> false;
        };
    }

    /**
     * WATCHING: gate on settings then place a buy order (MARKET or LIMIT at LTP).
     * Gates checked in order:
     * 1. Type-based toggle (place10MinOrders / placeDailyOrders)
     * 2. Max open-order cap for the same watchlist type
     * Status is set to BUY PLACED <em>before</em> the API call to prevent
     * duplicate orders if a second tick arrives concurrently.
     *
     * @return {@code true} if the trade reached a terminal state (rejected).
     */
    private boolean handleWatching(Trade trade, double ltp) {
        String type     = trade.getType();
        boolean is10Min = "10MinWatchlist".equals(type);
        boolean isDaily = "DailyWatchlist".equals(type);

        // ── Gate 1: enabled toggle ────────────────────────────────────────────
        if (is10Min && !settingService.getBoolean(SettingService.GRP_ORDERS, "place10MinOrders", true)) {
            log.info("TradeMonitor 10-min orders disabled — skipping trade id={} name={}",
                    trade.getId(), trade.getName());
            return false; // stay WATCHING
        }
        if (isDaily && !settingService.getBoolean(SettingService.GRP_ORDERS, "placeDailyOrders", true)) {
            log.info("TradeMonitor daily orders disabled — skipping trade id={} name={}",
                    trade.getId(), trade.getName());
            return false; // stay WATCHING
        }

        // ── Gate 2: max open-order cap ────────────────────────────────────────
        if (type != null && (is10Min || isDaily)) {
            int  maxOpen   = is10Min
                    ? settingService.getInt(SettingService.GRP_ORDERS, "max10MinOrders", 5)
                    : settingService.getInt(SettingService.GRP_ORDERS, "maxDailyOrders", 5);
            long openCount = countInCacheByTypeAndStatus(type, "OPEN");
            if (openCount >= maxOpen) {
                long pendingCount = countInCacheByTypeAndStatus(type, "BUY PLACED", "SELL PLACED");
                log.info("TradeMonitor max {} open ({}) reached for type '{}' — pending={} — waiting, trade id={}",
                        maxOpen, openCount, type, pendingCount, trade.getId());
                return false; // stay WATCHING — recheck on next tick
            }
        }

        // ── Proceed: place buy order ──────────────────────────────────────────
        String orderType = settingService.get(SettingService.GRP_ORDERS, "orderType", "MARKET");

        // Flip status first to prevent duplicate orders on concurrent ticks
        trade.setStatus("BUY PLACED");
        tradeRepository.save(trade);
        tradeSseBroadcaster.broadcastUpdate(trade);

        log.info("TradeMonitor WATCHING → BUY PLACED — id={} name={} ltp={} orderType={}",
                trade.getId(), trade.getName(), ltp, orderType);

        BrokerOrderService.OrderResult result = brokerOrderService.placeBuyOrder(trade, orderType, ltp);
        trade.setBuyOrderId(result.orderId);

        if (result.status == BrokerOrderService.OrderStatus.FILLED) {
            // Paper mode or instant fill: skip polling
            double fill = result.fillPrice > 0 ? result.fillPrice : ltp;
            onBuyFilled(trade, fill);
            tradeRepository.save(trade);
            return false; // Now OPEN — keep in cache
        } else if (result.status == BrokerOrderService.OrderStatus.REJECTED) {
            trade.setStatus("Broker Rejected: " + result.rejectionReason);
            tradeRepository.save(trade);
            tradeSseBroadcaster.broadcastUpdate(trade);
            return true; // Evict — terminal status
        }
        // PENDING: stays BUY PLACED, polled by order poller
        tradeRepository.save(trade);
        return false;
    }

    /**
     * BUY PLACED: poll order status (throttled to every 5 s on tick path).
     * At EOD: revert to OPEN so the position is tracked the next trading day.
     *
     * @return {@code true} if the trade reached a terminal state (rejected).
     */
    private boolean handleBuyPlaced(Trade trade) {
        if (isEod()) {
            log.info("TradeMonitor EOD — buy pending for trade id={}, marking OPEN for next day",
                    trade.getId());
            trade.setStatus("OPEN");
            trade.setBuyOrderId(null);
            tradeRepository.save(trade);
            return false;
        }
        if (!shouldPoll(trade.getId())) return false;

        BrokerOrderService.OrderResult result =
                brokerOrderService.checkOrderStatusWithFill(trade.getBuyOrderId());

        if (result.status == BrokerOrderService.OrderStatus.FILLED) {
            double fill = result.fillPrice > 0 ? result.fillPrice : trade.getBuyPrice();
            onBuyFilled(trade, fill);
            tradeRepository.save(trade);
            return false; // Now OPEN — keep in cache
        } else if (result.status == BrokerOrderService.OrderStatus.REJECTED) {
            trade.setStatus("Broker Rejected: " + result.rejectionReason);
            log.error("TradeMonitor buy order REJECTED for trade {} — Reason: {}",
                    trade.getId(), result.rejectionReason);
            tradeRepository.save(trade);
            return true; // Evict
        }
        return false; // Still pending
    }

    /**
     * OPEN: recalculate unrealised PnL on each tick.
     */
    private void handleOpen(Trade trade, double ltp) {
        double pnl = Math.round((ltp - trade.getBuyPrice()) * trade.getNoOfShares() * 100.0) / 100.0;
        trade.setPnl(pnl);
    }

    /**
     * SELL PLACED: poll order status (throttled to every 5 s on tick path).
     * At EOD: revert to OPEN for next day.
     *
     * @return {@code true} if the trade reached a terminal state (closed/rejected).
     */
    private boolean handleSellPlaced(Trade trade) {
        if (isEod()) {
            log.info("TradeMonitor EOD — sell pending for trade id={}, reverting to OPEN for next day",
                    trade.getId());
            trade.setStatus("OPEN");
            trade.setSellOrderId(null);
            tradeRepository.save(trade);
            return false;
        }
        if (!shouldPoll(trade.getId())) return false;

        BrokerOrderService.OrderResult result =
                brokerOrderService.checkOrderStatusWithFill(trade.getSellOrderId());

        if (result.status == BrokerOrderService.OrderStatus.FILLED) {
            double fill = result.fillPrice > 0 ? result.fillPrice : trade.getSellPrice();
            onSellFilled(trade, fill);
            tradeRepository.save(trade);
            return true; // Evict — CLOSED is terminal
        } else if (result.status == BrokerOrderService.OrderStatus.REJECTED) {
            trade.setStatus("OPEN");
            trade.setSellOrderId(null);
            log.error("TradeMonitor sell order REJECTED for trade {} — reverting to OPEN. Reason: {}",
                    trade.getId(), result.rejectionReason);
            tradeRepository.save(trade);
            return false; // Back to OPEN — keep in cache
        }
        return false; // Still pending
    }

    // ── State transitions ─────────────────────────────────────────────────────

    private void onBuyFilled(Trade trade, double fillPrice) {
        trade.setBuyPrice(fillPrice);
        trade.setStatus("OPEN");
        log.info("TradeMonitor BUY filled — id={} name={} buyPrice={}",
                trade.getId(), trade.getName(), fillPrice);
        tradeSseBroadcaster.broadcastUpdate(trade);
    }

    private void onSellFilled(Trade trade, double fillPrice) {
        trade.setSellPrice(fillPrice);
        double pnl = Math.round((fillPrice - trade.getBuyPrice()) * trade.getNoOfShares() * 100.0) / 100.0;
        trade.setPnl(pnl);
        trade.setStatus("CLOSED");
        log.info("TradeMonitor SELL filled — id={} name={} sellPrice={} PnL={}",
                trade.getId(), trade.getName(), fillPrice, pnl);
        tradeSseBroadcaster.broadcastUpdate(trade);
    }

    // ── Order poller (independent of tick stream) ─────────────────────────────

    /**
     * Starts a background thread that polls Zerodha at {@code pollingIntervalMinutes} for all
     * pending (BUY PLACED / SELL PLACED) orders, even when no ticks are arriving.
     */
    private void startOrderPoller() {
        stopOrderPoller(); // ensure no duplicate
        orderPoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trade-order-poller");
            t.setDaemon(true);
            return t;
        });
        orderPoller.scheduleAtFixedRate(this::pollPendingOrders,
                pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        log.info("TradeMonitor order poller started — interval={}ms", pollIntervalMs);
    }

    private void stopOrderPoller() {
        if (orderPoller != null) {
            orderPoller.shutdownNow();
            orderPoller = null;
        }
    }

    /**
     * Polls all trades in BUY PLACED or SELL PLACED status.
     * Runs on the dedicated order-poller thread every 5 seconds.
     */
    private void pollPendingOrders() {
        for (Trade trade : activeById.values()) {
            try {
                switch (trade.getStatus()) {
                    case "BUY PLACED" -> {
                        boolean evict = handleBuyPlaced(trade);
                        if (evict) evictTrade(trade);
                    }
                    case "SELL PLACED" -> {
                        boolean evict = handleSellPlaced(trade);
                        if (evict) evictTrade(trade);
                    }
                    default -> { /* nothing */ }
                }
            } catch (Exception e) {
                log.error("TradeMonitor error polling order for trade {}: {}", trade.getId(), e.getMessage());
            }
        }
    }

    // ── Public cache management ───────────────────────────────────────────────

    /**
     * Places a manual buy order for a WATCHING trade, bypassing auto-trigger gates.
     * Reuses the same broker call and state transitions as the automatic path.
     */
    public Trade placeManualBuyOrder(Trade trade) {
        // ── Gate: IndexOption orders controlled by "Place Option orders" setting ────
        if ("IndexOption".equals(trade.getType())) {
            if (!settingService.getBoolean(SettingService.GRP_ORDERS, "placeDailyOrders", true)) {
                log.info("TradeMonitor Option orders disabled — skipping trade id={} name={}",
                        trade.getId(), trade.getName());
                return trade; // stay WATCHING
            }
            int  maxOption   = settingService.getInt(SettingService.GRP_ORDERS, "maxDailyOrders", 5);
            long openOptions = countInCacheByTypeAndStatus("IndexOption", "OPEN");
            if (openOptions >= maxOption) {
                log.info("TradeMonitor max Option orders ({}) reached — skipping trade id={}",
                        maxOption, trade.getId());
                return trade; // stay WATCHING
            }
        }

        String orderType = settingService.get(SettingService.GRP_ORDERS, "orderType", "MARKET");

        trade.setStatus("BUY PLACED");
        tradeRepository.save(trade);

        log.info("TradeMonitor MANUAL BUY — id={} name={} orderType={}", trade.getId(), trade.getName(), orderType);

        BrokerOrderService.OrderResult result = brokerOrderService.placeBuyOrder(trade, orderType, trade.getBuyPrice());
        trade.setBuyOrderId(result.orderId);

        if (result.status == BrokerOrderService.OrderStatus.FILLED) {
            double fill = result.fillPrice > 0 ? result.fillPrice : trade.getBuyPrice();
            onBuyFilled(trade, fill);
        } else if (result.status == BrokerOrderService.OrderStatus.REJECTED) {
            trade.setStatus("Broker Rejected: " + result.rejectionReason);
        }
        tradeRepository.save(trade);
        tradeSseBroadcaster.broadcastUpdate(trade);
        registerTrade(trade);
        return trade;
    }

    /**
     * Places a manual sell order for an OPEN trade at the current buy price (MARKET order).
     * Reuses the same broker call and state transitions as the automatic path.
     */
    public Trade placeManualSellOrder(Trade trade) {
        String orderType = settingService.get(SettingService.GRP_ORDERS, "orderType", "MARKET");

        trade.setStatus("SELL PLACED");
        tradeRepository.save(trade);

        log.info("TradeMonitor MANUAL SELL — id={} name={} orderType={}", trade.getId(), trade.getName(), orderType);

        BrokerOrderService.OrderResult result = brokerOrderService.placeSellOrder(trade, orderType, trade.getBuyPrice());
        trade.setSellOrderId(result.orderId);

        if (result.status == BrokerOrderService.OrderStatus.FILLED) {
            double fill = result.fillPrice > 0 ? result.fillPrice : trade.getBuyPrice();
            onSellFilled(trade, fill);
            evictTrade(trade);
        } else if (result.status == BrokerOrderService.OrderStatus.REJECTED) {
            trade.setStatus("OPEN");
            trade.setSellOrderId(null);
            log.error("TradeMonitor manual sell order REJECTED for trade {} — reverting to OPEN. Reason: {}",
                    trade.getId(), result.rejectionReason);
        }
        // PENDING → stays SELL PLACED, polled by order-poller thread
        tradeRepository.save(trade);
        tradeSseBroadcaster.broadcastUpdate(trade);
        registerTrade(trade);
        return trade;
    }

    /**
     * Registers a trade into the live monitoring cache after a manual add or edit.
     * If the trade has an active status it is added/updated; otherwise it is evicted.
     * Safe to call when the stream is not running (no-op on empty cache).
     */
    public void registerTrade(Trade trade) {
        if (trade == null || trade.getId() == null) return;
        if (ACTIVE_STATUSES.contains(trade.getStatus())) {
            addToCache(trade);
            log.info("TradeMonitor trade registered — id={} name={} status={}",
                    trade.getId(), trade.getName(), trade.getStatus());
        } else {
            evictTrade(trade);
            log.info("TradeMonitor trade evicted (non-active status) — id={} name={} status={}",
                    trade.getId(), trade.getName(), trade.getStatus());
        }
    }

    // ── Cache helpers ─────────────────────────────────────────────────────────

    private void loadActiveTrades() {
        List<Trade> trades = tradeRepository.findByStatusIn(ACTIVE_STATUSES);
        for (Trade t : trades) {
            addToCache(t);
        }
    }

    private void addToCache(Trade trade) {
        if (trade.getId() == null) return;
        activeById.put(trade.getId(), trade);
        List<Trade> list = activeByToken.computeIfAbsent(
                trade.getToken(), k -> new CopyOnWriteArrayList<>());
        list.removeIf(t -> t.getId() != null && t.getId().equals(trade.getId()));
        list.add(trade);
    }

    private void evictTrade(Trade trade) {
        if (trade.getId() == null) return;
        activeById.remove(trade.getId());
        lastPollMs.remove(trade.getId());
        List<Trade> list = activeByToken.get(trade.getToken());
        if (list != null) list.remove(trade);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Counts in-memory active trades for a given watchlist type and one-or-more statuses. */
    private long countInCacheByTypeAndStatus(String type, String... statuses) {
        Set<String> statusSet = Set.of(statuses);
        return activeById.values().stream()
                .filter(t -> type.equals(t.getType()) && statusSet.contains(t.getStatus()))
                .count();
    }

    private boolean isEod() {
        return LocalTime.now(IST).compareTo(EOD_TIME) >= 0;
    }

    private boolean shouldPoll(Long tradeId) {
        long now  = System.currentTimeMillis();
        Long last = lastPollMs.get(tradeId);
        if (last == null || (now - last) >= pollIntervalMs) {
            lastPollMs.put(tradeId, now);
            return true;
        }
        return false;
    }
}
