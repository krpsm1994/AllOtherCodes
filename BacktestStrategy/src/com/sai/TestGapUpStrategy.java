package com.sai;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class TestGapUpStrategy {

    public static void main(String[] args) throws Exception {

        // --- Load tokens.json ---
        String tokensPath = "src/com/sai/tokens.json";
        String tokensJson = new String(Files.readAllBytes(Paths.get(tokensPath)));
        JSONArray tokensArray = new JSONArray(tokensJson);

        Map<String, String> tokenMap = new HashMap<>();
        for (int i = 0; i < tokensArray.length(); i++) {
            JSONObject entry = tokensArray.getJSONObject(i);
            tokenMap.put(entry.getString("symbol").trim(), String.valueOf(entry.get("token")));
        }
        System.out.println("Loaded " + tokenMap.size() + " tokens.");

        // --- Read CSV ---
        String csvPath = "src/com/sai/records_gap_up.csv";
        List<String> lines = Files.readAllLines(Paths.get(csvPath));

        // --- Print table header ---
        System.out.printf("%n%-15s %-12s %-12s %-10s %-10s %-10s %-12s %-12s %-14s %-15s %-14s %-14s %-14s%n",
                "Symbol", "CsvDate", "PrevDayHigh", "InitialSL", "BuyPrice", "Target",
                "TrailingSL", "TrailingHigh", "TargetCrossed", "Status",
                "WaitingDay", "TriggerDay", "ExitDay");
        System.out.println("-".repeat(185));

        DateTimeFormatter csvFmt = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("dd-MM-yyyy")
                .toFormatter(Locale.ENGLISH);
        DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

        HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
        Map<String, List<HistoricalDataFetcher.Candle>> candleCache = new HashMap<>();
        try {
            for (int i = 1; i < lines.size(); i++) {
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

                LocalDate csvDate = LocalDate.parse(date, csvFmt);

                // Fetch from 10 calendar days before csvDate (ensures >=2 trading days prior)
                // through 1 year after csvDate for exit tracking
                String fromDate = csvDate.minusDays(10).atStartOfDay().format(apiFmt);
                String toDate   = csvDate.plusDays(370).atStartOfDay().format(apiFmt);

                try {
                    String requestKey = String.join("|",
                            token,
                            HistoricalDataFetcher.Exchange.NSE.name(),
                            HistoricalDataFetcher.Interval.ONE_DAY.name(),
                            fromDate,
                            toDate);

                    List<HistoricalDataFetcher.Candle> candles = candleCache.get(requestKey);
                    if (candles == null) {
                        candles = fetcher.fetchCandleData(
                                HistoricalDataFetcher.Exchange.NSE,
                                token,
                                HistoricalDataFetcher.Interval.ONE_DAY,
                                fromDate,
                                toDate);
                        candleCache.put(requestKey, candles);
                    }

                    analyzeSymbol(symbol, csvDate, candles);
                } catch (Exception e) {
                    System.err.println("Error fetching candles for " + symbol + ": " + e.getMessage());
                }
            }
        } finally {
            fetcher.close();
        }
    }

    private static void analyzeSymbol(String symbol, LocalDate csvDate,
                                      List<HistoricalDataFetcher.Candle> candles) {

        // Find index of the first candle on or after csvDate
        int csvIdx = -1;
        for (int i = 0; i < candles.size(); i++) {
            LocalDate d = ZonedDateTime.parse(candles.get(i).timestamp).toLocalDate();
            if (!d.isBefore(csvDate)) { csvIdx = i; break; }
        }

        if (csvIdx < 2) {
            System.out.printf("%-15s %-12s %-48s %-15s%n",
                    symbol, csvDate, "-", "No Data");
            return;
        }

        // Rule 1-2: reference candles
        HistoricalDataFetcher.Candle minus2Candle = candles.get(csvIdx - 2); // D-2
        HistoricalDataFetcher.Candle minus1Candle = candles.get(csvIdx - 1); // D-1

        double initialStopLoss = minus2Candle.low;   // SL = D-2 low
        double prevDayHigh     = minus1Candle.high;  // threshold = D-1 high

        // State variables
        String  status       = "NA";
        String  waitingDay   = "-";
        String  triggerDay   = "-";
        String  exitDay      = "-";
        double  buyPrice     = 0;
        double  target       = 0;
        double  stopLoss     = initialStopLoss;
        double  trailingHigh = Double.MIN_VALUE;
        boolean triggered    = false;
        boolean waiting      = false;
        boolean targetCrossed        = false;
        boolean enableTrailingStopLoss = true;

        // Rule 3-5: From csvDate candle, search up to 30 candles for close > D-1 high
        int searchLimit = Math.min(csvIdx + 30, candles.size());

        for (int j = csvIdx; j < searchLimit; j++) {
            HistoricalDataFetcher.Candle c = candles.get(j);
            LocalDate day = ZonedDateTime.parse(c.timestamp).toLocalDate();

            // Rule 3-4: close > prevDayHigh → Waiting; buy price = that day's high
            if (c.close > prevDayHigh) {
                waiting    = true;
                status     = "Waiting";
                waitingDay = day.toString();
                buyPrice   = c.high;

                // Rule 6: compute target (1:2 R:R)
                target = buyPrice + (2.0 * (buyPrice - stopLoss));

                // Check if trigger already fired on the same candle (gap scenario)
                if (c.high > buyPrice) {
                    triggered    = true;
                    trailingHigh = c.high;
                    triggerDay   = day.toString();
                    status       = "Triggered";

                    if (enableTrailingStopLoss) {
                        if (c.high >= target) {
                            // Target crossed on trigger candle — jump SL to mid
                            stopLoss      = (buyPrice + target) / 2.0;
                            trailingHigh  = c.high;
                            targetCrossed = true;
                        } else {
                            // Regular trail from buyPrice as baseline on trigger candle
                            stopLoss += (c.high - buyPrice);
                        }
                    }
                    if (c.low <= stopLoss) {
                        status  = (targetCrossed || stopLoss > buyPrice) ? "Profit" : "Loss";
                        exitDay = day.toString();
                        return; // skip to print
                    }
                    status = "Open";
                }

                // Move to post-waiting loop
                // Continue from j+1
                int postWaitStart = j + 1;

                // Rule 6-7: watch for trigger then SL/Target
                for (int k = postWaitStart; k < candles.size(); k++) {
                    HistoricalDataFetcher.Candle ck = candles.get(k);
                    LocalDate ckDay = ZonedDateTime.parse(ck.timestamp).toLocalDate();

                    if (!triggered) {
                        // Trigger: candle high crosses buy price
                        if (ck.high > buyPrice) {
                            triggered    = true;
                            trailingHigh = ck.high;
                            triggerDay   = ckDay.toString();
                            status       = "Triggered";

                            if (enableTrailingStopLoss) {
                                if (ck.high >= target) {
                                    // Target crossed on trigger candle — jump SL to mid
                                    stopLoss      = (buyPrice + target) / 2.0;
                                    trailingHigh  = ck.high;
                                    targetCrossed = true;
                                } else {
                                    // Regular trail from buyPrice as baseline on trigger candle
                                    stopLoss += (ck.high - buyPrice);
                                }
                            }
                            if (ck.low <= stopLoss) {
                                status  = (targetCrossed || stopLoss > buyPrice) ? "Profit" : "Loss";
                                exitDay = ckDay.toString();
                                break;
                            }
                            status = "Open";
                        }
                    } else {
                        // Rule 7: Trailing stop loss
                        if (enableTrailingStopLoss) {
                            if (!targetCrossed && ck.high >= target) {
                                // First target cross: jump SL to mid(buyPrice, target)
                                stopLoss     = (buyPrice + target) / 2.0;
                                trailingHigh = ck.high;
                                targetCrossed = true;
                            } else if (ck.high > trailingHigh) {
                                // Regular trail: raise SL by the gain in high
                                double diff  = ck.high - trailingHigh;
                                stopLoss    += diff;
                                trailingHigh = ck.high;
                            }
                        }

                        if (ck.low <= stopLoss) {
                            status  = (targetCrossed || stopLoss > buyPrice) ? "Profit" : "Loss";
                            exitDay = ckDay.toString();
                            break;
                        }
                    }
                }
                break; // found the waiting day; done with search loop
            }
        }

        // Rule 5: if never reached Waiting in 30 days, status remains "NA"

        // --- Print result row ---
        String trailingHighStr = triggered ? String.format("%.2f", trailingHigh) : "-";
        String trailingSlStr   = triggered ? String.format("%.2f", stopLoss)     : "-";
        String buyPriceStr     = waiting   ? String.format("%.2f", buyPrice)     : "-";
        String targetStr       = waiting   ? String.format("%.2f", target)       : "-";

        System.out.printf("%-15s %-12s %-12.2f %-10.2f %-10s %-10s %-12s %-12s %-14s %-15s %-14s %-14s %-14s%n",
                symbol, csvDate, prevDayHigh, initialStopLoss,
                buyPriceStr, targetStr,
                trailingSlStr, trailingHighStr, targetCrossed,
                status, waitingDay, triggerDay, exitDay);
    }
}
