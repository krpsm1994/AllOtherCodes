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

public class TestStrategy1WithTrailSL {

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

		// Print single table header
		System.out.printf("%n%-15s %-12s %-10s %-10s %-10s %-12s %-12s %-20s %-22s %-22s %-30s%n",
				"Symbol", "Date", "BuyPrice", "InitialSL", "Target", "TrailSL", "TrailHigh", "Status", "TriggerTime", "ExitTime", "Pattern(15m)");
		System.out.println("-".repeat(190));

		DateTimeFormatter csvFmt = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern("yyyy-MM-dd h:mm a")
				.toFormatter(Locale.ENGLISH);
		DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

		HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
		Map<String, List<HistoricalDataFetcher.Candle>> candleCache    = new HashMap<>();
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

				LocalDateTime fromDt = LocalDateTime.parse(date, csvFmt);
				LocalDate targetDay  = fromDt.toLocalDate();
				LocalDateTime toDt   = fromDt.plusDays(30);
				String fromDate = fromDt.format(apiFmt);
				String toDate   = toDt.format(apiFmt);

				// 15-min candle range: 5 trading days back (covers weekends) to targetDay+1
				String fromDate15m = fromDt.minusDays(5).format(apiFmt);
				String toDate15m   = fromDt.plusDays(1).format(apiFmt);

				try {
					String requestKey = String.join("|",
							token,
							HistoricalDataFetcher.Exchange.NSE.name(),
							HistoricalDataFetcher.Interval.FIVE_MINUTE.name(),
							fromDate,
							toDate);

					List<HistoricalDataFetcher.Candle> candles = candleCache.get(requestKey);
					if (candles == null) {
						candles = fetcher.fetchCandleData(
								HistoricalDataFetcher.Exchange.NSE,
								token,
								HistoricalDataFetcher.Interval.FIVE_MINUTE,
								fromDate,
								toDate);
						candleCache.put(requestKey, candles);
					}

					String requestKey15m = String.join("|",
							token,
							HistoricalDataFetcher.Exchange.NSE.name(),
							HistoricalDataFetcher.Interval.FIFTEEN_MINUTE.name(),
							fromDate15m,
							toDate15m);

					List<HistoricalDataFetcher.Candle> candles15m = candleCache15m.get(requestKey15m);
					if (candles15m == null) {
						candles15m = fetcher.fetchCandleData(
								HistoricalDataFetcher.Exchange.NSE,
								token,
								HistoricalDataFetcher.Interval.FIFTEEN_MINUTE,
								fromDate15m,
								toDate15m);
						candleCache15m.put(requestKey15m, candles15m);
					}

					analyzeSymbol(symbol, targetDay, candles, candles15m);
				} catch (Exception e) {
					System.err.println("Error fetching candles for " + symbol + ": " + e.getMessage());
				}
			}
		} finally {
			fetcher.close();
		}
	}

	private static void analyzeSymbol(String symbol, LocalDate targetDay,
			List<HistoricalDataFetcher.Candle> candles,
			List<HistoricalDataFetcher.Candle> candles15m) {
		// Find the index of the first candle on targetDay
		int startIdx = -1;
		for (int i = 0; i < candles.size(); i++) {
			LocalDate d = ZonedDateTime.parse(candles.get(i).timestamp).toLocalDate();
			if (d.equals(targetDay)) { startIdx = i; break; }
		}
		if (startIdx < 0 || startIdx + 3 > candles.size()) {
			System.out.printf("%-15s %-12s %-10s %-10s %-10s %-15s%n",
					symbol, targetDay, "-", "-", "-", "No Data");
			return;
		}

		// Detect 15-min candle patterns for 9:15 AM on targetDay
		String pattern = CandlePatternUtility.detectOpeningCandlePatterns(candles15m, targetDay);

		// --- First 3 candles of targetDay: compute buyPrice and stopLoss ---
		double buyPrice = Double.MIN_VALUE;
		double stopLoss = Double.MAX_VALUE;
		for (int j = startIdx; j < startIdx + 3; j++) {
			HistoricalDataFetcher.Candle c = candles.get(j);
			if (c.high > buyPrice) buyPrice = c.high;
			if (c.low  < stopLoss) stopLoss = c.low;
		}
		double target          = 2 * buyPrice - stopLoss; // 1:1 risk-reward
		double initialStopLoss = stopLoss;

		String status        = "Not Triggered";
		String triggeredTime = "-";
		String exitTime      = "-";
		LocalTime triggerCutoff = LocalTime.of(12, 0);
		boolean triggered    = false;
		double trailingHigh  = Double.MIN_VALUE; // highest high seen after trigger

		// --- From 4th candle of targetDay, iterate ALL remaining candles ---
		for (int j = startIdx + 3; j < candles.size(); j++) {
			HistoricalDataFetcher.Candle c = candles.get(j);
			ZonedDateTime zdt        = ZonedDateTime.parse(c.timestamp);
			LocalDate     candleDay  = zdt.toLocalDate();
			LocalTime     candleTime = zdt.toLocalTime();

			if (!triggered) {
				// Moved past targetDay without triggering
				if (!candleDay.equals(targetDay)) {
					status = "Not Triggered";
					break;
				}
				// Rule 3: trigger window closed at 12:00 PM
				if (!candleTime.isBefore(triggerCutoff)) {
					status = "Not Triggered";
					break;
				}
				// Rule 2: SL hit before buy price triggered
				if (c.low <= stopLoss) {
					status  = "Not Triggered";
					exitTime = candleDay + " " + candleTime;
					break;
				}
				// Rule 1: buy price crossed — trigger entry
				if (c.high > buyPrice) {
					triggered     = true;
					triggeredTime = candleDay + " " + candleTime;
					status        = "Open";
					trailingHigh  = c.high;
					// Update trailSL: initialSL + 2 * (newHigh - buyPrice)
					stopLoss = initialStopLoss + 2.0 * (trailingHigh - buyPrice);
					// Same candle: check target hit first, then SL
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
			} else {
				// Update trailing high and trail SL if new high is made
				if (c.high > trailingHigh) {
					trailingHigh = c.high;
					// trailSL = initialSL + 2 * (newHigh - buyPrice)
					stopLoss = initialStopLoss + 2.0 * (trailingHigh - buyPrice);
				}
				// Rule 4: target crossed — Profit
				if (c.high >= target) {
					status   = "Profit";
					exitTime = candleDay + " " + candleTime;
					break;
				}
				// Rule 5: SL hit — Loss or Profit depending on where trailSL is
				if (c.low <= stopLoss) {
					status   = stopLoss > buyPrice ? "Profit" : "Loss";
					exitTime = candleDay + " " + candleTime;
					break;
				}
			}
		}

		String trailSLStr   = triggered ? String.format("%.2f", stopLoss)      : "-";
		String trailHighStr = triggered ? String.format("%.2f", trailingHigh)  : "-";
		System.out.printf("%-15s %-12s %-10.2f %-10.2f %-10.2f %-12s %-12s %-20s %-22s %-22s %-30s%n",
				symbol, targetDay, buyPrice, initialStopLoss, target, trailSLStr, trailHighStr, status, triggeredTime, exitTime, pattern);
	}

}
