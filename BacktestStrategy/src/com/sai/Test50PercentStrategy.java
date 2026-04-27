package com.sai;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Strategy: 50% Candle Close Filter with 15-Min BuyPrice/SL
 *
 * Rules:
 *  1. BuyPrice = 15-min opening candle high on targetDay
 *  2. SL       = 15-min opening candle low  on targetDay
 *  3. Trade window: 11:00 AM – 12:00 PM only
 *  4. Before 11:00 AM: if any 3-min candle high > buyPrice  → Not Triggered
 *                       if close < 50% of candle range       → Not Triggered
 *  5. After  12:00 PM: no new triggers                       → Not Triggered
 *  6. 11:00 AM – 12:00 PM: if high > buyPrice               → Trigger (Open)
 *  7. Before trigger: close < 50% of candle range            → Not Triggered
 *  8. After trigger: target = 2 * buyPrice - SL
 *
 * Uses 3-min candles over 50 days.
 */
public class Test50PercentStrategy {

    public static void main(String[] args) throws Exception {

        // --- Load tokens.json into a symbol -> token map ---
        String tokensPath = "src/com/sai/tokens.json";
        String tokensJson = new String(Files.readAllBytes(Paths.get(tokensPath)));
        JSONArray tokensArray = new JSONArray(tokensJson);

        Map<String, String> tokenMap = new HashMap<>();
        for (int i = 0; i < tokensArray.length(); i++) {
            JSONObject entry = tokensArray.getJSONObject(i);
            tokenMap.put(entry.getString("symbol").trim(), String.valueOf(entry.get("token")));
        }
        System.out.println("Loaded " + tokenMap.size() + " tokens.");

        // --- Read records.csv, skip header, resolve token per row ---
        String csvPath = "src/com/sai/records.csv";
        List<String> lines = Files.readAllLines(Paths.get(csvPath));

        // Print table header
        System.out.printf("%n%-15s %-12s %-10s %-10s %-10s %-20s %-22s %-22s %-30s%n",
                "Symbol", "Date", "BuyPrice", "InitialSL", "Target", "Status", "TriggerTime", "ExitTime", "Pattern(15m)");
        System.out.println("-".repeat(165));

        DateTimeFormatter csvFmt = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("yyyy-MM-dd h:mm a")
                .toFormatter(Locale.ENGLISH);
        DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

        HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
        Map<String, List<HistoricalDataFetcher.Candle>> candleCache3m  = new HashMap<>();
        Map<String, List<HistoricalDataFetcher.Candle>> candleCache15m = new HashMap<>();

        try {
            for (int i = 1; i < lines.size(); i++) {   // skip header row
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split(",");
                if (parts.length < 2) continue;

                String date   = parts[0].trim();
                String symbol = parts[1].trim();

                String token = tokenMap.getOrDefault(symbol, "NOT FOUND");
                if (token.equals("NOT FOUND")) {
                    System.out.println("Token not found for symbol: " + symbol);
                    continue;
                }

                LocalDateTime fromDt    = LocalDateTime.parse(date, csvFmt);
                LocalDate     targetDay = fromDt.toLocalDate();

                // 3-min candle range: targetDay to +50 days (max 60 days allowed by API)
                LocalDateTime toDt = fromDt.plusDays(50);
                String fromDate   = fromDt.format(apiFmt);
                String toDate     = toDt.format(apiFmt);

                // 15-min candle range: 5 trading days back to targetDay+1 (for pattern detection)
                String fromDate15m = fromDt.minusDays(5).format(apiFmt);
                String toDate15m   = fromDt.plusDays(1).format(apiFmt);

                try {
                    // Fetch 3-min candles
                    String requestKey3m = String.join("|",
                            token,
                            HistoricalDataFetcher.Exchange.NSE.name(),
                            HistoricalDataFetcher.Interval.FIFTEEN_MINUTE.name(),
                            fromDate, toDate);

                    List<HistoricalDataFetcher.Candle> candles3m = candleCache3m.get(requestKey3m);
                    if (candles3m == null) {
                        candles3m = fetcher.fetchCandleData(
                                HistoricalDataFetcher.Exchange.NSE,
                                token,
                                HistoricalDataFetcher.Interval.THREE_MINUTE,
                                fromDate, toDate);
                        candleCache3m.put(requestKey3m, candles3m);
                    }

                    // Fetch 15-min candles
                    String requestKey15m = String.join("|",
                            token,
                            HistoricalDataFetcher.Exchange.NSE.name(),
                            HistoricalDataFetcher.Interval.FIFTEEN_MINUTE.name(),
                            fromDate15m, toDate15m);

                    List<HistoricalDataFetcher.Candle> candles15m = candleCache15m.get(requestKey15m);
                    if (candles15m == null) {
                        candles15m = fetcher.fetchCandleData(
                                HistoricalDataFetcher.Exchange.NSE,
                                token,
                                HistoricalDataFetcher.Interval.FIFTEEN_MINUTE,
                                fromDate15m, toDate15m);
                        candleCache15m.put(requestKey15m, candles15m);
                    }

                    analyzeSymbol(symbol, targetDay, candles3m, candles15m);
                } catch (Exception e) {
                    System.err.println("Error fetching candles for " + symbol + ": " + e.getMessage());
                }
            }
        } finally {
            fetcher.close();
        }
    }

    private static void analyzeSymbol(String symbol, LocalDate targetDay,
            List<HistoricalDataFetcher.Candle> candles3m,
            List<HistoricalDataFetcher.Candle> candles15m) {

        // --- Derive BuyPrice and SL from the first 15-min candle on targetDay ---
        double buyPrice = -1;
        double stopLoss = -1;
        for (HistoricalDataFetcher.Candle c : candles15m) {
            LocalDate d = ZonedDateTime.parse(c.timestamp).toLocalDate();
            if (d.equals(targetDay)) {
                buyPrice = c.high;   // Rule 1
                stopLoss = c.low;    // Rule 2
                break;
            }
        }

        if (buyPrice < 0) {
            System.out.printf("%-15s %-12s %-10s %-10s %-10s %-20s %-22s %-22s %-30s%n",
                    symbol, targetDay, "-", "-", "-", "No15mData", "-", "-", "-");
            return;
        }

        double target          = 2 * buyPrice - stopLoss;  // Rule 8
        double initialStopLoss = stopLoss;

        // Detect 15-min candle patterns for targetDay
        String pattern = CandlePatternUtility.detectOpeningCandlePatterns(candles15m, targetDay);

        // --- Find the first 3-min candle on targetDay ---
        int startIdx = -1;
        for (int i = 0; i < candles3m.size(); i++) {
            LocalDate d = ZonedDateTime.parse(candles3m.get(i).timestamp).toLocalDate();
            if (d.equals(targetDay)) { startIdx = i; break; }
        }

        if (startIdx < 0) {
            System.out.printf("%-15s %-12s %-10.2f %-10.2f %-10.2f %-20s %-22s %-22s %-30s%n",
                    symbol, targetDay, buyPrice, initialStopLoss, target, "No3mData", "-", "-", pattern);
            return;
        }

        LocalTime tradeStart   = LocalTime.of(11, 0);
        LocalTime tradeCutoff  = LocalTime.of(12, 0);

        String  status        = "Not Triggered";
        String  triggeredTime = "-";
        String  exitTime      = "-";
        boolean triggered     = false;

        for (int j = startIdx; j < candles3m.size(); j++) {
            HistoricalDataFetcher.Candle c = candles3m.get(j);
            ZonedDateTime zdt        = ZonedDateTime.parse(c.timestamp);
            LocalDate     candleDay  = zdt.toLocalDate();
            LocalTime     candleTime = zdt.toLocalTime();

            if (!triggered) {
                // Pre-trigger logic is restricted to targetDay only
                if (!candleDay.equals(targetDay)) {
                    // Day ended without a valid trigger
                    status = "Not Triggered";
                    break;
                }

                // Rule 5: after 12 PM no new triggers
                if (!candleTime.isBefore(tradeCutoff)) {
                    status = "Not Triggered";
                    break;
                }

                // Rules 4 & 7: close below 50% of candle range → Not Triggered
                double range = c.high - c.low;
                if (range > 0 && c.close < c.low + 0.5 * range) {
                    status = "Not Triggered";
                    break;
                }

                if (candleTime.isBefore(tradeStart)) {
                    // Rule 4 (before 11 AM): high crosses buyPrice → invalidated
                    if (c.high > buyPrice) {
                        status = "Not Triggered";
                        break;
                    }
                } else {
                    // Rule 6 (11 AM – 12 PM): high crosses buyPrice → trigger
                    if (c.high > buyPrice) {
                        triggered     = true;
                        triggeredTime = candleDay + " " + candleTime;
                        status        = "Open";
                        // Same candle: check target first, then SL
                        if (c.high >= target) {
                            status   = "Profit";
                            exitTime = candleDay + " " + candleTime;
                            break;
                        }
                        if (c.low <= stopLoss) {
                            status   = "Loss";
                            exitTime = candleDay + " " + candleTime;
                            break;
                        }
                    }
                }
            } else {
                // Rule 8: monitor target / SL across all subsequent candles
                if (c.high >= target) {
                    status   = "Profit";
                    exitTime = candleDay + " " + candleTime;
                    break;
                }
                if (c.low <= stopLoss) {
                    status   = "Loss";
                    exitTime = candleDay + " " + candleTime;
                    break;
                }
            }
        }

        System.out.printf("%-15s %-12s %-10.2f %-10.2f %-10.2f %-20s %-22s %-22s %-30s%n",
                symbol, targetDay, buyPrice, initialStopLoss, target,
                status, triggeredTime, exitTime, pattern);
    }
}
