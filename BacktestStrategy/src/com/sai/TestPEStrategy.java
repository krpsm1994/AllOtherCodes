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

public class TestPEStrategy {

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

		// --- Read records_sell.csv, skip header, resolve token per row ---
		String csvPath = "src/com/sai/records_sell.csv";
		List<String> lines = Files.readAllLines(Paths.get(csvPath));

		// Print single table header
		System.out.printf("%n%-15s %-12s %-10s %-10s %-10s %-12s %-12s %-14s %-20s %-22s %-22s%n",
				"Symbol", "Date", "SellPrice", "InitialSL", "Target", "TrailingSL", "TrailingLow", "TargetCrossed", "Status", "TriggerTime", "ExitTime");
		System.out.println("-".repeat(170));

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

			// Parse CSV date "dd-MM-yyyy h:mm a" -> API format "yyyy-MM-dd HH:mm"
			DateTimeFormatter csvFmt = new DateTimeFormatterBuilder()
					.parseCaseInsensitive()
					.appendPattern("dd-MM-yyyy h:mm a")
					.toFormatter(Locale.ENGLISH);
			DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);
			LocalDateTime fromDt = LocalDateTime.parse(date, csvFmt);
			LocalDate targetDay  = fromDt.toLocalDate();
			LocalDateTime toDt   = fromDt.plusDays(15);
			String fromDate = fromDt.format(apiFmt);
			String toDate   = toDt.format(apiFmt);

			try {
				HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
				List<HistoricalDataFetcher.Candle> candles = fetcher.fetchCandleData(
						HistoricalDataFetcher.Exchange.NSE,
						token,
						HistoricalDataFetcher.Interval.FIVE_MINUTE,
						fromDate,
						toDate);
				analyzeSymbol(symbol, targetDay, candles);
				fetcher.close();
			} catch (Exception e) {
				System.err.println("Error fetching candles for " + symbol + ": " + e.getMessage());
			}
		}
	}

	private static void analyzeSymbol(String symbol, LocalDate targetDay, List<HistoricalDataFetcher.Candle> candles) {
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

		// --- First 3 candles of targetDay: sellPrice = min low, stopLoss = max high ---
		double sellPrice = Double.MAX_VALUE;
		double stopLoss  = Double.MIN_VALUE;
		for (int j = startIdx; j < startIdx + 3; j++) {
			HistoricalDataFetcher.Candle c = candles.get(j);
			if (c.low  < sellPrice) sellPrice = c.low;
			if (c.high > stopLoss)  stopLoss  = c.high;
		}
		double target          = 2 * sellPrice - stopLoss; // 1:1 risk-reward (below sellPrice)
		double initialStopLoss = stopLoss;                  // preserve original SL before any trailing

		String status        = "Not Triggered";
		String triggeredTime = "-";
		String exitTime      = "-";
		LocalTime cutoff     = LocalTime.of(11, 0);
		boolean triggered    = false;
		boolean enableTrailingStopLoss = true;
		boolean targetCrossed  = false;               // true once low has crossed target at least once
		double trailingLow     = Double.MAX_VALUE;    // tracks lowest low seen after trigger

		// --- From 4th candle of targetDay, iterate ALL remaining candles ---
		for (int j = startIdx + 3; j < candles.size(); j++) {
			HistoricalDataFetcher.Candle c = candles.get(j);
			ZonedDateTime zdt    = ZonedDateTime.parse(c.timestamp);
			LocalDate candleDay  = zdt.toLocalDate();
			LocalTime candleTime = zdt.toLocalTime();

			if (!triggered) {
				// Trigger window: only on targetDay before 11:00 AM
				if (!candleDay.equals(targetDay) || !candleTime.isBefore(cutoff)) {
					status = "Not Triggered";
					break;
				}
				// Trigger: candle low breaks below sellPrice
				if (c.low < sellPrice) {
					triggered    = true;
					trailingLow  = c.low;
					triggeredTime = candleDay + " " + candleTime;

					// Check target cross on trigger candle — bump SL to mid, don't exit
					if (enableTrailingStopLoss && c.low <= target) {
						stopLoss      = (sellPrice + target) / 2.0;
						trailingLow   = c.low;
						targetCrossed = true;
					}
					// Stoploss hit on same trigger candle
					if (c.high >= stopLoss) { status = targetCrossed ? "Profit" : "Loss"; exitTime = candleDay + " " + candleTime; break; }
					status = "Open";
				}
			} else {
				if (enableTrailingStopLoss) {
					// First time low crosses target: jump SL to mid(sellPrice, target) and reset trailing reference
					if (!targetCrossed && c.low <= target) {
						stopLoss      = (sellPrice + target) / 2.0;
						trailingLow   = c.low;
						targetCrossed = true;
					} else if (c.low < trailingLow) {
						// Regular trailing: lower SL by the same amount the low moved down
						double diff  = trailingLow - c.low;
						stopLoss    -= diff;
						trailingLow  = c.low;
					}
				}

				// Exit on SL hit (candle high crosses above stopLoss)
				if (c.high >= stopLoss) {
					status   = targetCrossed ? "Profit" : "Loss";
					exitTime = candleDay + " " + candleTime;
					break;
				}
			}
		}

		String trailingLowStr = triggered ? String.format("%.2f", trailingLow) : "-";
		String trailingSlStr  = triggered ? String.format("%.2f", stopLoss)    : "-";
		System.out.printf("%-15s %-12s %-10.2f %-10.2f %-10.2f %-12s %-12s %-14s %-20s %-22s %-22s%n",
				symbol, targetDay, sellPrice, initialStopLoss, target, trailingSlStr, trailingLowStr, targetCrossed, status, triggeredTime, exitTime);
	}

}
