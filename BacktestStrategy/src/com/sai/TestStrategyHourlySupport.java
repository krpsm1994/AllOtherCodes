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

public class TestStrategyHourlySupport {

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
		String csvPath = "src/com/sai/records_hourly.csv";
		List<String> lines = Files.readAllLines(Paths.get(csvPath));

		// Print single table header
		System.out.printf("%n%-15s %-20s %-10s %-10s %-10s %-20s %-22s %-22s %-10s %-10s %-15s %-22s %-12s %-30s%n",
				"Symbol", "Date", "BuyPrice", "InitialSL", "Target", "Status", "TriggerTime", "ExitTime",
				"TrailHigh", "TrailSL", "TrailStatus", "TrailExitTime", "TrailP/L", "Pattern(15m)");
		System.out.println("-".repeat(235));

		DateTimeFormatter csvFmt = new DateTimeFormatterBuilder()
				.parseCaseInsensitive()
				.appendPattern("dd-MM-yyyy h:mm a")
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
				LocalDateTime toDt   = fromDt.plusDays(10);
				String fromDate = fromDt.format(apiFmt);
				String toDate   = toDt.format(apiFmt);

				// 15-min candle range: 5 trading days back (covers weekends) to targetDay+1
				String fromDate15m = fromDt.minusDays(5).format(apiFmt);
				String toDate15m   = fromDt.plusDays(1).format(apiFmt);

				try {
					String requestKey = String.join("|",
							token,
							HistoricalDataFetcher.Exchange.NSE.name(),
							HistoricalDataFetcher.Interval.THREE_MINUTE.name(),
							fromDate,
							toDate);

					List<HistoricalDataFetcher.Candle> candles = candleCache.get(requestKey);
					if (candles == null) {
						candles = fetcher.fetchCandleData(
								HistoricalDataFetcher.Exchange.NSE,
								token,
								HistoricalDataFetcher.Interval.THREE_MINUTE,
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

					analyzeSymbol(symbol, date, targetDay, candles, candles15m);
				} catch (Exception e) {
					System.err.println("Error fetching candles for " + symbol + ": " + e.getMessage());
				}
			}
		} finally {
			fetcher.close();
		}
	}

	private static void analyzeSymbol(String symbol, String csvDate, LocalDate targetDay,
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
		double target          = (2 * buyPrice) - stopLoss; // 1:2 risk-reward
		double initialStopLoss = stopLoss;

		String status        = "Not Triggered";
		String triggeredTime = "-";
		String exitTime      = "-";
		// Rule 3: trigger must happen on a candle starting before 12:00 PM (i.e. latest 11:55 AM candle)
		LocalTime triggerCutoff = LocalTime.of(12, 0);
		boolean triggered = false;
		int count = 0;
		// --- From 4th candle of targetDay, iterate ALL remaining candles ---
		for (int j = startIdx + 3; j < candles.size(); j++) {
			HistoricalDataFetcher.Candle c = candles.get(j);
			ZonedDateTime zdt        = ZonedDateTime.parse(c.timestamp);
			LocalDate     candleDay  = zdt.toLocalDate();
			LocalTime     candleTime = zdt.toLocalTime();

			if (!triggered) {
				// Rule 1: buy price crossed — trigger entry
				if (c.high > buyPrice) {
					triggered     = true;
					triggeredTime = candleDay + " " + candleTime;
					status        = "Open";
					// Same candle: check target hit first, then SL
					if (c.high >= target) {
						status   = "Profit";
						exitTime = candleDay + " " + candleTime;
						break;
					}
					// Same candle also hit SL — not a clean trigger; check 15m close
					if (c.low <= stopLoss) {
						double close15m = find15mClose(candles15m, candleDay, candleTime);
						triggered = false;
						status    = "Not Triggered";
						if (close15m >= 0 && close15m < initialStopLoss) {
							break; // 15m close below SL — stop looking
						}
						// 15m close still above SL — continue iterating
					}
				} else {
					// High did not cross buyPrice — check if low hit SL before trigger
					if (c.low <= stopLoss) {
						double close15m = find15mClose(candles15m, candleDay, candleTime);
						if (close15m >= 0 && close15m < initialStopLoss) {
							status = "Not Triggered";
							break; // 15m close below SL — stop looking
						}
						// 15m close still above SL — continue iterating
					} else {
						count++;
						if (count >= 20) {
							status = "Not Triggered";
							break;
						}
					}
				}
			} else {
				// Rule 4: target crossed — Profit
				if (c.high >= target) {
					status   = "Profit";
					exitTime = candleDay + " " + candleTime;
					break;
				}
				// Rule 5: SL hit — Loss
				if (c.low <= stopLoss) {
					status   = "Loss";
					exitTime = candleDay + " " + candleTime;
					break;
				}
			}
		}

		// --- Trailing SL: milestone-based step-up logic ---
		// Milestones based on (target - buyPrice) range
		double trailRange   = target - buyPrice;
		double milestone50  = buyPrice + 0.50 * trailRange;  // 50%  of range above buyPrice
		double milestone85  = buyPrice + 0.85 * trailRange;  // 85%  of range above buyPrice

		double trailHigh     = 0;
		double trailSL       = initialStopLoss;
		String trailStatus   = "Not Triggered";
		String trailExitTime = "-";
		boolean trailActive  = false;

		for (int j = startIdx + 3; j < candles.size(); j++) {
			HistoricalDataFetcher.Candle tc = candles.get(j);
			ZonedDateTime tzdt        = ZonedDateTime.parse(tc.timestamp);
			LocalDate     tCandleDay  = tzdt.toLocalDate();
			LocalTime     tCandleTime = tzdt.toLocalTime();

			if (!trailActive) {
				// Activate when high crosses buyPrice
				if (tc.high > buyPrice) {
					trailActive = true;
					trailHigh   = tc.high;
					trailStatus = "Open";
					// Apply milestone SL steps on the entry candle itself
					if (trailHigh >= milestone85) {
						trailSL = milestone50;
					} else if (trailHigh >= milestone50) {
						trailSL = buyPrice;
					}
					// Check target hit on entry candle
					if (trailHigh >= target) {
						trailStatus   = "Target Hit";
						trailExitTime = tCandleDay + " " + tCandleTime;
						break;
					}
					// Check SL hit on entry candle — not a clean activate; check 15m close
					if (tc.low <= trailSL) {
						double close15m = find15mClose(candles15m, tCandleDay, tCandleTime);
						trailActive = false;
						trailStatus = "Not Triggered";
						if (close15m >= 0 && close15m < initialStopLoss) {
							break; // 15m close below SL — stop looking
						}
						// 15m close still above SL — continue iterating
					}
				}
			} else {
				// Update running high
				if (tc.high > trailHigh) {
					trailHigh = tc.high;
				}
				// Step up trailSL based on milestones (only move up, never down)
				if (trailHigh >= milestone85) {
					trailSL = Math.max(trailSL, milestone50);
				} else if (trailHigh >= milestone50) {
					trailSL = Math.max(trailSL, buyPrice);
				}
				// Exit: new high reached target
				if (trailHigh >= target) {
					trailStatus   = "Target Hit";
					trailExitTime = tCandleDay + " " + tCandleTime;
					break;
				}
				// Exit: low hits trailing SL
				if (tc.low <= trailSL) {
					trailStatus   = "TSL Exit";
					trailExitTime = tCandleDay + " " + tCandleTime;
					break;
				}
			}
		}

		String trailPnL = "-";
		if (trailActive) {
			if (trailSL == buyPrice) {
				trailPnL = "Neutral";
			} else if (trailSL > buyPrice) {
				trailPnL = "Profit";
			} else {
				trailPnL = "Loss";
			}
		}

		System.out.printf("%-15s %-20s %-10.2f %-10.2f %-10.2f %-20s %-22s %-22s %-10.2f %-10.2f %-15s %-22s %-12s %-30s%n",
				symbol, csvDate, buyPrice, initialStopLoss, target, status, triggeredTime, exitTime,
				trailHigh, trailSL, trailStatus, trailExitTime, trailPnL, pattern);
	}

	/**
	 * Returns the close of the latest 15-min candle on the given day whose start
	 * time is at or before the given time. Returns -1 if no matching candle found.
	 */
	private static double find15mClose(List<HistoricalDataFetcher.Candle> candles15m,
			LocalDate day, LocalTime time) {
		double close = -1;
		for (HistoricalDataFetcher.Candle c : candles15m) {
			ZonedDateTime zdt = ZonedDateTime.parse(c.timestamp);
			LocalDate cDay  = zdt.toLocalDate();
			LocalTime cTime = zdt.toLocalTime();
			if (cDay.equals(day) && !cTime.isAfter(time)) {
				close = c.close; // keep updating — last one at or before 'time' wins
			} else if (cDay.isAfter(day)) {
				break;
			}
		}
		return close;
	}

}
