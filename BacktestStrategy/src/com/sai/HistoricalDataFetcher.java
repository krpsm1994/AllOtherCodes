package com.sai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches historical candle data and OI data from AngelOne SmartAPI.
 *
 * Docs: https://smartapi.angelone.in/docs/Historical
 *
 * Endpoint (Candle): POST https://apiconnect.angelone.in/rest/secure/angelbroking/historical/v1/getCandleData
 * Endpoint (OI):     POST https://apiconnect.angelone.in/rest/secure/angelbroking/historical/v1/getOIData
 */
public class HistoricalDataFetcher {

    // -----------------------------------------------------------------------
    // API Config — replace placeholders before running
    // -----------------------------------------------------------------------
    private static final String API_KEY            = "fYy1t3Zh";          // X-PrivateKey
    private static final String AUTHORIZATION_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VybmFtZSI6IlM4MTI1NTkiLCJyb2xlcyI6MCwidXNlcnR5cGUiOiJVU0VSIiwidG9rZW4iOiJleUpoYkdjaU9pSlNVekkxTmlJc0luUjVjQ0k2SWtwWFZDSjkuZXlKMWMyVnlYM1I1Y0dVaU9pSmpiR2xsYm5RaUxDSjBiMnRsYmw5MGVYQmxJam9pZEhKaFpHVmZZV05qWlhOelgzUnZhMlZ1SWl3aVoyMWZhV1FpT2pFekxDSnpiM1Z5WTJVaU9pSXpJaXdpWkdWMmFXTmxYMmxrSWpvaU5tRTRObVkzTW1RdE9UVmtNaTB6WldGaUxUa3paR1V0TXpRek9HSTNaVGc0TXpBM0lpd2lhMmxrSWpvaWRISmhaR1ZmYTJWNVgzWXlJaXdpYjIxdVpXMWhibUZuWlhKcFpDSTZNVE1zSW5CeWIyUjFZM1J6SWpwN0ltUmxiV0YwSWpwN0luTjBZWFIxY3lJNkltRmpkR2wyWlNKOUxDSnRaaUk2ZXlKemRHRjBkWE1pT2lKaFkzUnBkbVVpZlN3aWJtSjFUR1Z1WkdsdVp5STZleUp6ZEdGMGRYTWlPaUpoWTNScGRtVWlmWDBzSW1semN5STZJblJ5WVdSbFgyeHZaMmx1WDNObGNuWnBZMlVpTENKemRXSWlPaUpUT0RFeU5UVTVJaXdpWlhod0lqb3hOemMwTWpNeE56VTFMQ0p1WW1ZaU9qRTNOelF4TkRVeE56VXNJbWxoZENJNk1UYzNOREUwTlRFM05Td2lhblJwSWpvaU9UaGpPR0kwWVdVdE1HRm1ZeTAwTlRBeExXSTNZVEF0WlRJM05qQmhZak0wTWpkaUlpd2lWRzlyWlc0aU9pSWlmUS5XVHVjSTVnVERqemE5VC0zTElzbEhzY3BjTld6dm4yekp2cnVVNUtEczhleWl1MHZUS0ttUG5obmRCQjdITzRmeGFaWUVwYmtubjZTU3ExaVhRTFNtYWdCSXpSZUdhMjRSTnF3NEdHdGZTQXJOZGxQTWczVDFVeGJERTBET1l1eVhHVWpyNEY4Y1RiOFdBT0xRaUZ0VEJzSmEwMXcxSTdad29Yc0dIeFd3NlEiLCJBUEktS0VZIjoiZll5MXQzWmgiLCJYLU9MRC1BUEktS0VZIjpmYWxzZSwiaWF0IjoxNzc0MTQ1MzU1LCJleHAiOjE3NzQyMDQyMDB9.RS_T6axYoCvTU-bjMjUAiyYkHHd6zyc4Vizhxdw2Xjuz8vMGuTg_p2_L38-8-KU5o0Ji-PHgJDeLid5z5BvU-A";       // Bearer token (JWT)
    private static final String CLIENT_LOCAL_IP    = "127.0.0.1";
    private static final String CLIENT_PUBLIC_IP   = "YOUR_PUBLIC_IP";
    private static final String MAC_ADDRESS        = "YOUR_MAC_ADDRESS";

    // -----------------------------------------------------------------------
    // Endpoints
    // -----------------------------------------------------------------------
    private static final String BASE_URL      = "https://apiconnect.angelone.in/rest/secure/angelbroking/historical/v1";
    private static final String CANDLE_URL    = BASE_URL + "/getCandleData";
    private static final String OI_URL        = BASE_URL + "/getOIData";

    // -----------------------------------------------------------------------
    // Global request throttling and retry policy
    // -----------------------------------------------------------------------
    private static final long REQUEST_GAP_MS         = 720L;
    private static final int MAX_RATE_LIMIT_RETRIES  = 4;
    private static final long BASE_RETRY_DELAY_MS    = 1500L;
    private static final Object RATE_LIMIT_LOCK      = new Object();
    private static long lastRequestEpochMs           = 0L;

    // -----------------------------------------------------------------------
    // Exchange constants (as per API docs)
    // -----------------------------------------------------------------------
    public enum Exchange {
        NSE,   // NSE Stocks and Indices
        NFO,   // NSE Futures and Options
        BSE,   // BSE Stocks and Indices
        BFO,   // BSE Futures and Options
        CDS,   // Currency Derivatives
        MCX    // Commodities Exchange
    }

    // -----------------------------------------------------------------------
    // Interval constants (as per API docs)
    // Max days per interval: ONE_MINUTE=30, THREE_MINUTE=60, FIVE_MINUTE=100,
    // TEN_MINUTE=100, FIFTEEN_MINUTE=200, THIRTY_MINUTE=200, ONE_HOUR=400, ONE_DAY=2000
    // -----------------------------------------------------------------------
    public enum Interval {
        ONE_MINUTE,
        THREE_MINUTE,
        FIVE_MINUTE,
        TEN_MINUTE,
        FIFTEEN_MINUTE,
        THIRTY_MINUTE,
        ONE_HOUR,
        ONE_DAY
    }

    // -----------------------------------------------------------------------
    // Candle data record: [timestamp, open, high, low, close, volume]
    // -----------------------------------------------------------------------
    public static class Candle {
        public final String timestamp;
        public final double open;
        public final double high;
        public final double low;
        public final double close;
        public final long   volume;

        public Candle(String timestamp, double open, double high, double low, double close, long volume) {
            this.timestamp = timestamp;
            this.open      = open;
            this.high      = high;
            this.low       = low;
            this.close     = close;
            this.volume    = volume;
        }

        @Override
        public String toString() {
            return String.format("[%s] O=%.2f H=%.2f L=%.2f C=%.2f V=%d",
                    timestamp, open, high, low, close, volume);
        }
    }

    // -----------------------------------------------------------------------
    // OI data record: { time, oi }
    // -----------------------------------------------------------------------
    public static class OIData {
        public final String time;
        public final long   oi;

        public OIData(String time, long oi) {
            this.time = time;
            this.oi   = oi;
        }

        @Override
        public String toString() {
            return String.format("[%s] OI=%d", time, oi);
        }
    }

    // -----------------------------------------------------------------------
    // Shared Java HttpClient (thread-safe, reusable)
    // -----------------------------------------------------------------------
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static void waitForGlobalRequestSlot() throws InterruptedException {
        synchronized (RATE_LIMIT_LOCK) {
            long now = System.currentTimeMillis();
            long waitMs = REQUEST_GAP_MS - (now - lastRequestEpochMs);
            if (waitMs > 0) {
                Thread.sleep(waitMs);
            }
            lastRequestEpochMs = System.currentTimeMillis();
        }
    }

    private static boolean isRateLimitResponse(int statusCode, String responseBody,
                                               String message, String errorCode) {
        if (statusCode == 429) {
            return true;
        }

        String combined = (responseBody == null ? "" : responseBody)
                + " " + (message == null ? "" : message)
                + " " + (errorCode == null ? "" : errorCode);
        String normalized = combined.toLowerCase(Locale.ENGLISH);

        return normalized.contains("too many request")
                || normalized.contains("too many requests")
                || normalized.contains("rate limit")
                || normalized.contains("exceeding access rate")
                || normalized.contains("rate exceeded")
                || normalized.contains("throttle");
    }

    private static void sleepWithBackoff(int attempt) throws InterruptedException {
        long delayMs = BASE_RETRY_DELAY_MS * attempt;
        Thread.sleep(delayMs);
    }

    // -----------------------------------------------------------------------
    // Helper: build an HttpRequest with all required headers
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
    // Fetch candle data
    //
    // @param exchange    Exchange enum (e.g. Exchange.NSE)
    // @param symbolToken Instrument token from scrip master (e.g. "3045" for Reliance)
    // @param interval    Interval enum (e.g. Interval.ONE_MINUTE)
    // @param fromDate    "yyyy-MM-dd HH:mm" (e.g. "2023-09-06 09:15")
    // @param toDate      "yyyy-MM-dd HH:mm" (e.g. "2023-09-06 15:30")
    // @return List of Candle objects
    // -----------------------------------------------------------------------
    public List<Candle> fetchCandleData(Exchange exchange, String symbolToken,
                                        Interval interval, String fromDate, String toDate)
            throws Exception {

        // Build JSON request body
        JSONObject body = new JSONObject();
        body.put("exchange",    exchange.name());
        body.put("symboltoken", symbolToken);
        body.put("interval",    interval.name());
        body.put("fromdate",    fromDate);
        body.put("todate",      toDate);

        for (int attempt = 1; attempt <= MAX_RATE_LIMIT_RETRIES + 1; attempt++) {
            waitForGlobalRequestSlot();
            //System.out.println(body.toString());
            HttpRequest request = buildPostRequest(CANDLE_URL, body.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            //System.out.println(statusCode);
            String responseBody = response.body();

            if (statusCode != 200) {
                if (attempt <= MAX_RATE_LIMIT_RETRIES
                        && isRateLimitResponse(statusCode, responseBody, "", "")) {
                    sleepWithBackoff(attempt);
                    continue;
                }
                throw new RuntimeException("Candle API error [HTTP " + statusCode + "]: " + responseBody);
            }

            JSONObject root;
            try {
                root = new JSONObject(responseBody);
            } catch (Exception parseEx) {
                throw new RuntimeException("Unable to parse candle API response: " + responseBody, parseEx);
            }

            if (!root.optBoolean("status", false)) {
                String message = root.optString("message");
                String errorCode = root.optString("errorcode");
                if (attempt <= MAX_RATE_LIMIT_RETRIES
                        && isRateLimitResponse(statusCode, responseBody, message, errorCode)) {
                    sleepWithBackoff(attempt);
                    continue;
                }

                throw new RuntimeException("API returned failure: " + message
                        + " | errorcode=" + errorCode);
            }

            List<Candle> candles = new ArrayList<>();
            JSONArray data = root.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONArray record = data.getJSONArray(i);
                String timestamp = record.getString(0);
                double open      = record.getDouble(1);
                double high      = record.getDouble(2);
                double low       = record.getDouble(3);
                double close     = record.getDouble(4);
                long   volume    = record.getLong(5);
                candles.add(new Candle(timestamp, open, high, low, close, volume));
            }
            return candles;
        }

        throw new RuntimeException("Candle API rate limit retries exhausted.");
    }

    // -----------------------------------------------------------------------
    // Fetch historical Open Interest (OI) data — only for F&O contracts (NFO, BFO)
    //
    // @param exchange    Exchange enum (e.g. Exchange.NFO)
    // @param symbolToken Instrument token from scrip master (e.g. "46823")
    // @param interval    Interval enum (e.g. Interval.THREE_MINUTE)
    // @param fromDate    "yyyy-MM-dd HH:mm"
    // @param toDate      "yyyy-MM-dd HH:mm"
    // @return List of OIData objects
    // -----------------------------------------------------------------------
    public List<OIData> fetchOIData(Exchange exchange, String symbolToken,
                                    Interval interval, String fromDate, String toDate)
            throws Exception {

        // Build JSON request body
        JSONObject body = new JSONObject();
        body.put("exchange",    exchange.name());
        body.put("symboltoken", symbolToken);
        body.put("interval",    interval.name());
        body.put("fromdate",    fromDate);
        body.put("todate",      toDate);

        for (int attempt = 1; attempt <= MAX_RATE_LIMIT_RETRIES + 1; attempt++) {
            waitForGlobalRequestSlot();

            HttpRequest request = buildPostRequest(OI_URL, body.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode != 200) {
                if (attempt <= MAX_RATE_LIMIT_RETRIES
                        && isRateLimitResponse(statusCode, responseBody, "", "")) {
                    sleepWithBackoff(attempt);
                    continue;
                }
                throw new RuntimeException("OI API error [HTTP " + statusCode + "]: " + responseBody);
            }

            JSONObject root;
            try {
                root = new JSONObject(responseBody);
            } catch (Exception parseEx) {
                throw new RuntimeException("Unable to parse OI API response: " + responseBody, parseEx);
            }

            if (!root.optBoolean("status", false)) {
                String message = root.optString("message");
                String errorCode = root.optString("errorcode");
                if (attempt <= MAX_RATE_LIMIT_RETRIES
                        && isRateLimitResponse(statusCode, responseBody, message, errorCode)) {
                    sleepWithBackoff(attempt);
                    continue;
                }

                throw new RuntimeException("API returned failure: " + message
                        + " | errorcode=" + errorCode);
            }

            List<OIData> oiList = new ArrayList<>();
            JSONArray data = root.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject record = data.getJSONObject(i);
                String time = record.getString("time");
                long   oi   = record.getLong("oi");
                oiList.add(new OIData(time, oi));
            }
            return oiList;
        }

        throw new RuntimeException("OI API rate limit retries exhausted.");
    }

    // -----------------------------------------------------------------------
    // No-op close kept for API compatibility (HttpClient needs no explicit close)
    // -----------------------------------------------------------------------
    public void close() {
        // java.net.http.HttpClient is managed by GC
    }

}
