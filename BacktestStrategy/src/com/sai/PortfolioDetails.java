package com.sai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches portfolio data from AngelOne SmartAPI.
 *
 * Docs: https://smartapi.angelone.in/docs/Portfolio
 *
 * Endpoints:
 *   GET  /portfolio/v1/getHolding       — individual holdings
 *   GET  /portfolio/v1/getAllHolding     — all holdings + totals summary
 *   GET  /order/v1/getPosition          — net & day positions
 *   POST /order/v1/convertPosition      — convert position margin product
 */
public class PortfolioDetails {

    // -----------------------------------------------------------------------
    // API Config — replace placeholders before running
    // -----------------------------------------------------------------------
    private static final String API_KEY             = "fYy1t3Zh";       // X-PrivateKey
    private static final String AUTHORIZATION_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VybmFtZSI6IlM4MTI1NTkiLCJyb2xlcyI6MCwidXNlcnR5cGUiOiJVU0VSIiwidG9rZW4iOiJleUpoYkdjaU9pSlNVekkxTmlJc0luUjVjQ0k2SWtwWFZDSjkuZXlKMWMyVnlYM1I1Y0dVaU9pSmpiR2xsYm5RaUxDSjBiMnRsYmw5MGVYQmxJam9pZEhKaFpHVmZZV05qWlhOelgzUnZhMlZ1SWl3aVoyMWZhV1FpT2pFekxDSnpiM1Z5WTJVaU9pSXpJaXdpWkdWMmFXTmxYMmxrSWpvaU5tRTRObVkzTW1RdE9UVmtNaTB6WldGaUxUa3paR1V0TXpRek9HSTNaVGc0TXpBM0lpd2lhMmxrSWpvaWRISmhaR1ZmYTJWNVgzWXlJaXdpYjIxdVpXMWhibUZuWlhKcFpDSTZNVE1zSW5CeWIyUjFZM1J6SWpwN0ltUmxiV0YwSWpwN0luTjBZWFIxY3lJNkltRmpkR2wyWlNKOUxDSnRaaUk2ZXlKemRHRjBkWE1pT2lKaFkzUnBkbVVpZlN3aWJtSjFUR1Z1WkdsdVp5STZleUp6ZEdGMGRYTWlPaUpoWTNScGRtVWlmWDBzSW1semN5STZJblJ5WVdSbFgyeHZaMmx1WDNObGNuWnBZMlVpTENKemRXSWlPaUpUT0RFeU5UVTVJaXdpWlhod0lqb3hOemN5T0RjeE1UZ3hMQ0p1WW1ZaU9qRTNOekkzT0RRMk1ERXNJbWxoZENJNk1UYzNNamM0TkRZd01Td2lhblJwSWpvaVlXRm1aVFpoTldJdFpEZ3haUzAwTTJJMExUa3lPREV0TnpZek9XUTBNbVkwTURJeElpd2lWRzlyWlc0aU9pSWlmUS5NVHp5bEh4TENFQkt6T0hUOHk0R04wSzYzWXNFMThiY25RWFFwU3RMMlZEaHN0UU4teUFIVW9rU25xTTJuM3ZURWhrOFkwcndYWEJHN3dPME1WT3M1MWFNSnYyLUdvdG1zV1ZwX3FScEJ2RHZDemxUc1VtVzUwb3FYQXlyaGNqOERKUUN2SDRfUUZVU0E5TEZTbVJnZFFEbllaVFhUdmhXUjRQX2J5X1A2VTgiLCJBUEktS0VZIjoiZll5MXQzWmgiLCJYLU9MRC1BUEktS0VZIjpmYWxzZSwiaWF0IjoxNzcyNzg0NzgxLCJleHAiOjE3NzI4MjE4MDB9.k4_x8h3Pf4l221YmasggjYOfaNLs8FNiKpJ7jugdg7S14-vcxymdmB0CUpAH6bHe8faxiQYBeO5eyvIusyR0jQ";     // Bearer token (JWT)
    private static final String CLIENT_LOCAL_IP     = "127.0.0.1";
    private static final String CLIENT_PUBLIC_IP    = "YOUR_PUBLIC_IP";
    private static final String MAC_ADDRESS         = "YOUR_MAC_ADDRESS";

    // -----------------------------------------------------------------------
    // Endpoints
    // -----------------------------------------------------------------------
    private static final String BASE_PORTFOLIO = "https://apiconnect.angelone.in/rest/secure/angelbroking/portfolio/v1";
    private static final String BASE_ORDER     = "https://apiconnect.angelone.in/rest/secure/angelbroking/order/v1";

    private static final String GET_HOLDING_URL      = BASE_PORTFOLIO + "/getHolding";
    private static final String GET_ALL_HOLDING_URL  = BASE_PORTFOLIO + "/getAllHolding";
    private static final String GET_POSITION_URL     = BASE_ORDER     + "/getPosition";
    private static final String CONVERT_POSITION_URL = BASE_ORDER     + "/convertPosition";

    // -----------------------------------------------------------------------
    // Holding — one stock in the DEMAT portfolio
    // -----------------------------------------------------------------------
    public static class Holding {
        public final String tradingSymbol;
        public final String exchange;
        public final String isin;
        public final int    t1Quantity;
        public final int    realisedQuantity;
        public final int    quantity;
        public final int    authorisedQuantity;
        public final String product;
        public final double haircut;
        public final double averagePrice;
        public final double ltp;
        public final String symbolToken;
        public final double close;
        public final double profitAndLoss;
        public final double pnlPercentage;

        public Holding(JSONObject j) {
            this.tradingSymbol      = j.optString("tradingsymbol");
            this.exchange           = j.optString("exchange");
            this.isin               = j.optString("isin");
            this.t1Quantity         = j.optInt("t1quantity");
            this.realisedQuantity   = j.optInt("realisedquantity");
            this.quantity           = j.optInt("quantity");
            this.authorisedQuantity = j.optInt("authorisedquantity");
            this.product            = j.optString("product");
            this.haircut            = j.optDouble("haircut");
            this.averagePrice       = j.optDouble("averageprice");
            this.ltp                = j.optDouble("ltp");
            this.symbolToken        = j.optString("symboltoken");
            this.close              = j.optDouble("close");
            this.profitAndLoss      = j.optDouble("profitandloss");
            this.pnlPercentage      = j.optDouble("pnlpercentage");
        }

        @Override
        public String toString() {
            return String.format("%-20s | Qty: %3d | Avg: %8.2f | LTP: %8.2f | P&L: %8.2f (%.2f%%)",
                    tradingSymbol, quantity, averagePrice, ltp, profitAndLoss, pnlPercentage);
        }
    }

    // -----------------------------------------------------------------------
    // TotalHolding — aggregated summary returned by getAllHolding
    // -----------------------------------------------------------------------
    public static class TotalHolding {
        public final double totalHoldingValue;
        public final double totalInvValue;
        public final double totalProfitAndLoss;
        public final double totalPnlPercentage;

        public TotalHolding(JSONObject j) {
            this.totalHoldingValue  = j.optDouble("totalholdingvalue");
            this.totalInvValue      = j.optDouble("totalinvvalue");
            this.totalProfitAndLoss = j.optDouble("totalprofitandloss");
            this.totalPnlPercentage = j.optDouble("totalpnlpercentage");
        }

        @Override
        public String toString() {
            return String.format("Total Value: %.2f | Invested: %.2f | P&L: %.2f (%.2f%%)",
                    totalHoldingValue, totalInvValue, totalProfitAndLoss, totalPnlPercentage);
        }
    }

    // -----------------------------------------------------------------------
    // AllHoldingsResponse — combines individual holdings + summary
    // -----------------------------------------------------------------------
    public static class AllHoldingsResponse {
        public final List<Holding> holdings;
        public final TotalHolding  totalHolding;

        public AllHoldingsResponse(List<Holding> holdings, TotalHolding totalHolding) {
            this.holdings     = holdings;
            this.totalHolding = totalHolding;
        }
    }

    // -----------------------------------------------------------------------
    // Position — a net or day position
    // -----------------------------------------------------------------------
    public static class Position {
        public final String exchange;
        public final String symbolToken;
        public final String productType;
        public final String tradingSymbol;
        public final String symbolName;
        public final String buyQty;
        public final String sellQty;
        public final String buyAmount;
        public final String sellAmount;
        public final String netQty;
        public final String netPrice;
        public final String buyAvgPrice;
        public final String sellAvgPrice;
        public final String netValue;
        public final String totalBuyValue;
        public final String totalSellValue;

        public Position(JSONObject j) {
            this.exchange       = j.optString("exchange");
            this.symbolToken    = j.optString("symboltoken");
            this.productType    = j.optString("producttype");
            this.tradingSymbol  = j.optString("tradingsymbol");
            this.symbolName     = j.optString("symbolname");
            this.buyQty         = j.optString("buyqty");
            this.sellQty        = j.optString("sellqty");
            this.buyAmount      = j.optString("buyamount");
            this.sellAmount     = j.optString("sellamount");
            this.netQty         = j.optString("netqty");
            this.netPrice       = j.optString("netprice");
            this.buyAvgPrice    = j.optString("buyavgprice");
            this.sellAvgPrice   = j.optString("sellavgprice");
            this.netValue       = j.optString("netvalue");
            this.totalBuyValue  = j.optString("totalbuyvalue");
            this.totalSellValue = j.optString("totalsellvalue");
        }

        @Override
        public String toString() {
            return String.format("%-20s | NetQty: %4s | BuyAvg: %10s | NetValue: %12s",
                    tradingSymbol, netQty, buyAvgPrice, netValue);
        }
    }

    // -----------------------------------------------------------------------
    // Shared HttpClient
    // -----------------------------------------------------------------------
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // -----------------------------------------------------------------------
    // Helper: GET request with all required headers
    // -----------------------------------------------------------------------
    private HttpRequest buildGetRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-PrivateKey",     API_KEY)
                .header("Accept",           "application/json")
                .header("X-SourceID",       "WEB")
                .header("X-ClientLocalIP",  CLIENT_LOCAL_IP)
                .header("X-ClientPublicIP", CLIENT_PUBLIC_IP)
                .header("X-MACAddress",     MAC_ADDRESS)
                .header("X-UserType",       "USER")
                .header("Authorization",    "Bearer " + AUTHORIZATION_TOKEN)
                .header("Content-Type",     "application/json")
                .GET()
                .build();
    }

    // -----------------------------------------------------------------------
    // Helper: POST request with all required headers
    // -----------------------------------------------------------------------
    private HttpRequest buildPostRequest(String url, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-PrivateKey",     API_KEY)
                .header("Accept",           "application/json")
                .header("X-SourceID",       "WEB")
                .header("X-ClientLocalIP",  CLIENT_LOCAL_IP)
                .header("X-ClientPublicIP", CLIENT_PUBLIC_IP)
                .header("X-MACAddress",     MAC_ADDRESS)
                .header("X-UserType",       "USER")
                .header("Authorization",    "Bearer " + AUTHORIZATION_TOKEN)
                .header("Content-Type",     "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    // -----------------------------------------------------------------------
    // Helper: validate API response and return the root JSONObject
    // -----------------------------------------------------------------------
    private JSONObject parseAndValidate(HttpResponse<String> response, String context) {
        if (response.statusCode() != 200) {
            throw new RuntimeException(context + " error [HTTP " + response.statusCode() + "]: " + response.body());
        }
        System.out.println(response.body());
        JSONObject root = new JSONObject(response.body());
        if (!root.getBoolean("status")) {
            throw new RuntimeException(context + " failure: " + root.optString("message")
                    + " | errorcode=" + root.optString("errorcode"));
        }
        return root;
    }

    // -----------------------------------------------------------------------
    // GET /portfolio/v1/getHolding
    // Returns individual holdings (long-term DEMAT equity delivery positions).
    // -----------------------------------------------------------------------
    public List<Holding> getHolding() throws Exception {
        HttpRequest request  = buildGetRequest(GET_HOLDING_URL);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject root = parseAndValidate(response, "getHolding");

        List<Holding> holdings = new ArrayList<>();
        JSONArray data = root.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                holdings.add(new Holding(data.getJSONObject(i)));
            }
        }
        return holdings;
    }

    // -----------------------------------------------------------------------
    // GET /portfolio/v1/getAllHolding
    // Returns all holdings plus a totalholding summary block.
    // -----------------------------------------------------------------------
    public AllHoldingsResponse getAllHolding() throws Exception {
        HttpRequest request  = buildGetRequest(GET_ALL_HOLDING_URL);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject root = parseAndValidate(response, "getAllHolding");

        JSONObject data = root.getJSONObject("data");

        List<Holding> holdings = new ArrayList<>();
        JSONArray holdingsArray = data.optJSONArray("holdings");
        if (holdingsArray != null) {
            for (int i = 0; i < holdingsArray.length(); i++) {
                holdings.add(new Holding(holdingsArray.getJSONObject(i)));
            }
        }

        TotalHolding totalHolding = new TotalHolding(data.getJSONObject("totalholding"));
        return new AllHoldingsResponse(holdings, totalHolding);
    }

    // -----------------------------------------------------------------------
    // GET /order/v1/getPosition
    // Returns net and day positions currently open.
    // -----------------------------------------------------------------------
    public List<Position> getPosition() throws Exception {
        HttpRequest request  = buildGetRequest(GET_POSITION_URL);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject root = parseAndValidate(response, "getPosition");

        List<Position> positions = new ArrayList<>();
        JSONArray data = root.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                positions.add(new Position(data.getJSONObject(i)));
            }
        }
        return positions;
    }

    // -----------------------------------------------------------------------
    // POST /order/v1/convertPosition
    // Converts a position from one margin product to another
    // (e.g. DELIVERY → INTRADAY).
    //
    // @param exchange         Exchange (e.g. "NSE")
    // @param symbolToken      Instrument token (e.g. "2885")
    // @param tradingSymbol    Trading symbol (e.g. "RELIANCE-EQ")
    // @param symbolName       Symbol name (e.g. "RELIANCE")
    // @param oldProductType   Current product type (e.g. "DELIVERY")
    // @param newProductType   Target product type (e.g. "INTRADAY")
    // @param transactionType  "BUY" or "SELL"
    // @param quantity         Number of units to convert
    // @param type             "DAY" or "IOC"
    // -----------------------------------------------------------------------
    public boolean convertPosition(String exchange, String symbolToken,
                                   String tradingSymbol, String symbolName,
                                   String oldProductType, String newProductType,
                                   String transactionType, int quantity, String type)
            throws Exception {

        JSONObject body = new JSONObject();
        body.put("exchange",        exchange);
        body.put("symboltoken",     symbolToken);
        body.put("oldproducttype",  oldProductType);
        body.put("newproducttype",  newProductType);
        body.put("tradingsymbol",   tradingSymbol);
        body.put("symbolname",      symbolName);
        body.put("instrumenttype",  "");
        body.put("priceden",        "1");
        body.put("pricenum",        "1");
        body.put("genden",          "1");
        body.put("gennum",          "1");
        body.put("precision",       "2");
        body.put("multiplier",      "-1");
        body.put("boardlotsize",    "1");
        body.put("lotsize",         "1");
        body.put("transactiontype", transactionType);
        body.put("quantity",        quantity);
        body.put("type",            type);

        HttpRequest request  = buildPostRequest(CONVERT_POSITION_URL, body.toString());
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        parseAndValidate(response, "convertPosition");   // throws on failure
        return true;
    }

    // -----------------------------------------------------------------------
    // Demo main
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        PortfolioDetails portfolio = new PortfolioDetails();
        try {
            // --- Holdings (individual) ---
            System.out.println("=== Holdings ===");
            List<Holding> holdings = portfolio.getHolding();
            holdings.forEach(System.out::println);

            // --- All Holdings + summary ---
            System.out.println("\n=== All Holdings ===");
            AllHoldingsResponse allHoldings = portfolio.getAllHolding();
            allHoldings.holdings.forEach(System.out::println);
            System.out.println("\n" + allHoldings.totalHolding);

            // --- Positions ---
            System.out.println("\n=== Positions ===");
            List<Position> positions = portfolio.getPosition();
            positions.forEach(System.out::println);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
