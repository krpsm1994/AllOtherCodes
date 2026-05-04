package self.sai.stock.AlgoTrading.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import self.sai.stock.AlgoTrading.entity.Trade;

import java.util.List;
import java.util.Map;

/**
 * Places and monitors Zerodha Kite Connect MARKET orders for AlgoTrading trades.
 *
 * <ul>
 *   <li>variety  : regular</li>
 *   <li>order_type: MARKET</li>
 *   <li>product  : CNC</li>
 *   <li>validity : DAY</li>
 * </ul>
 *
 * Set {@code trading.paper=true} in application.properties to run in paper-trading
 * mode (no real orders sent, all treated as instantly FILLED).
 */
@Service
public class BrokerOrderService {

    private static final Logger log = LoggerFactory.getLogger(BrokerOrderService.class);

    private static final String KITE_BASE = "https://api.kite.trade";

    @Value("${zerodha.api.key:7mov9qt27tpmk2ft}")
    private String apiKey;

    @Value("${trading.paper:false}")
    private boolean paperTrading;

    @Value("${trading.exchange:NSE}")
    private String exchange;

    private final RestTemplate restTemplate;
    private final ZerodhaSessionStoreService sessionStore;

    public BrokerOrderService(RestTemplate restTemplate,
                              ZerodhaSessionStoreService sessionStore) {
        this.restTemplate = restTemplate;
        this.sessionStore  = sessionStore;
    }

    // ── Result types ──────────────────────────────────────────────────────────

    public enum OrderStatus { FILLED, REJECTED, PENDING }

    public static class OrderResult {
        public final String      orderId;
        public final OrderStatus status;
        public final double      fillPrice;
        public final String      rejectionReason;

        public OrderResult(String orderId, OrderStatus status, double fillPrice) {
            this(orderId, status, fillPrice, null);
        }

        public OrderResult(String orderId, OrderStatus status, double fillPrice, String rejectionReason) {
            this.orderId         = orderId;
            this.status          = status;
            this.fillPrice       = fillPrice;
            this.rejectionReason = rejectionReason != null ? rejectionReason : "";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Places a BUY order. Order type and limit price are controlled by the caller.
     * For LIMIT orders pass the desired price; MARKET ignores {@code limitPrice}.
     */
    public OrderResult placeBuyOrder(Trade trade, String orderType, double limitPrice) {
        int qty = trade.getNoOfShares();
        if (qty <= 0) {
            log.warn("[Kite] BUY qty=0 — id={} name={}", trade.getId(), trade.getName());
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, "qty=0");
        }
        return placeKiteOrder("BUY", trade.getName(), qty, "AT" + trade.getId(), orderType, limitPrice);
    }

    /**
     * Places a SELL order. Order type and limit price are controlled by the caller.
     * For LIMIT orders pass the desired price; MARKET ignores {@code limitPrice}.
     */
    public OrderResult placeSellOrder(Trade trade, String orderType, double limitPrice) {
        int qty = trade.getNoOfShares();
        if (qty <= 0) {
            log.warn("[Kite] SELL qty=0 — id={} name={}", trade.getId(), trade.getName());
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, "qty=0");
        }
        return placeKiteOrder("SELL", trade.getName(), qty, "AT" + trade.getId(), orderType, limitPrice);
    }

    /**
     * Places a MARKET BUY order. Quantity is taken from {@code trade.noOfShares}.
     */
    public OrderResult placeBuyOrder(Trade trade) {
        int qty = trade.getNoOfShares();
        if (qty <= 0) {
            log.warn("[Kite] BUY qty=0 — id={} name={}", trade.getId(), trade.getName());
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, "qty=0");
        }
        return placeKiteOrder("BUY", trade.getName(), qty, "AT" + trade.getId(), "MARKET", 0.0);
    }

    /**
     * Places a MARKET SELL order. Quantity is taken from {@code trade.noOfShares}.
     */
    public OrderResult placeSellOrder(Trade trade) {
        int qty = trade.getNoOfShares();
        if (qty <= 0) {
            log.warn("[Kite] SELL qty=0 — id={} name={}", trade.getId(), trade.getName());
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, "qty=0");
        }
        return placeKiteOrder("SELL", trade.getName(), qty, "AT" + trade.getId(), "MARKET", 0.0);
    }

    /**
     * Checks the live status and fill price of an existing order from Zerodha.
     *
     * @return {@link OrderResult} with {@link OrderStatus#FILLED},
     *         {@link OrderStatus#REJECTED}, or {@link OrderStatus#PENDING}.
     */
    public OrderResult checkOrderStatusWithFill(String orderId) {
        if (orderId == null) {
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, "orderId is null");
        }
        if (paperTrading || orderId.startsWith("PAPER-")) {
            log.debug("[Kite] Paper mode — orderId={} → FILLED", orderId);
            return new OrderResult(orderId, OrderStatus.FILLED, 0.0);
        }

        String accessToken = sessionStore.getAccessToken();
        if (accessToken == null) {
            log.error("[Kite] No Zerodha session — cannot check order {}", orderId);
            return new OrderResult(orderId, OrderStatus.PENDING, 0.0);
        }

        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    KITE_BASE + "/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(accessToken)),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = resp.getBody();
            if (body == null || !"success".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                return new OrderResult(orderId, OrderStatus.PENDING, 0.0);
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data == null || data.isEmpty()) {
                return new OrderResult(orderId, OrderStatus.PENDING, 0.0);
            }

            // Last entry in order history = current state
            Map<String, Object> last = data.get(data.size() - 1);
            String kiteStatus = String.valueOf(last.getOrDefault("status", "")).toUpperCase();
            String reason     = String.valueOf(last.getOrDefault("status_message", ""));

            double fillPrice = 0.0;
            Object avgPrice  = last.get("average_price");
            if (avgPrice != null) {
                try { fillPrice = Double.parseDouble(String.valueOf(avgPrice)); } catch (NumberFormatException ignored) {}
            }

            log.debug("[Kite] Order {} → kiteStatus={} fillPrice={}", orderId, kiteStatus, fillPrice);

            return switch (kiteStatus) {
                case "COMPLETE"              -> new OrderResult(orderId, OrderStatus.FILLED,   fillPrice);
                case "REJECTED", "CANCELLED" -> new OrderResult(orderId, OrderStatus.REJECTED, fillPrice, reason);
                default                      -> new OrderResult(orderId, OrderStatus.PENDING,  fillPrice);
            };
        } catch (HttpStatusCodeException ex) {
            log.error("[Kite] HTTP error for order {}: {} — {}", orderId,
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return new OrderResult(orderId, OrderStatus.PENDING, 0.0);
        } catch (Exception ex) {
            log.error("[Kite] Exception for order {}: {}", orderId, ex.getMessage());
            return new OrderResult(orderId, OrderStatus.PENDING, 0.0);
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private OrderResult placeKiteOrder(String txType, String symbol, int qty, String tag,
                                       String orderType, double limitPrice) {
        boolean isLimit = "LIMIT".equalsIgnoreCase(orderType);
        log.info("[Kite] {} {} qty={} exchange={} orderType={} tag={}", txType, symbol, qty, exchange, isLimit ? "LIMIT" : "MARKET", tag);

        if (paperTrading) {
            String paperId = "PAPER-" + System.currentTimeMillis();
            log.info("[Kite] PAPER mode — orderId={} (auto-filled)", paperId);
            return new OrderResult(paperId, OrderStatus.FILLED, 0.0);
        }

        String accessToken = sessionStore.getAccessToken();
        if (accessToken == null) {
            log.error("[Kite] No Zerodha session — cannot place {} order for {}", txType, symbol);
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, "No Zerodha session");
        }

        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("tradingsymbol",    symbol);
            form.add("exchange",         exchange);
            form.add("transaction_type", txType);
            form.add("order_type",       isLimit ? "LIMIT" : "MARKET");
            form.add("product",          "CNC");
            form.add("validity",         "DAY");
            form.add("quantity",         String.valueOf(qty));
            form.add("tag",              tag);
            if (isLimit && limitPrice > 0) {
                form.add("price", String.format("%.2f", limitPrice));
            }

            HttpHeaders headers = buildHeaders(accessToken);
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    KITE_BASE + "/orders/regular",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});

            Map<String, Object> body = resp.getBody();
            if (body == null || !"success".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                String err = body != null ? String.valueOf(body.get("message")) : "null response";
                log.error("[Kite] {} order placement failed: {}", txType, err);
                return new OrderResult(null, OrderStatus.REJECTED, 0.0, err);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = (Map<String, Object>) body.get("data");
            String orderId = responseData != null ? String.valueOf(responseData.get("order_id")) : null;
            log.info("[Kite] {} order placed — orderId={}", txType, orderId);
            return new OrderResult(orderId, OrderStatus.PENDING, 0.0);

        } catch (HttpStatusCodeException ex) {
            String err = ex.getResponseBodyAsString();
            log.error("[Kite] HTTP error placing {} order for {}: {}", txType, symbol, err);
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, err);
        } catch (Exception ex) {
            log.error("[Kite] Exception placing {} order for {}: {}", txType, symbol, ex.getMessage());
            return new OrderResult(null, OrderStatus.REJECTED, 0.0, ex.getMessage());
        }
    }

    private HttpHeaders buildHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        String effectiveApiKey = sessionStore.getApiKey() != null ? sessionStore.getApiKey() : apiKey;
        headers.set("Authorization",  "token " + effectiveApiKey + ":" + accessToken);
        headers.set("X-Kite-Version", "3");
        return headers;
    }
}
