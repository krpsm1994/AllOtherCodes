package com.sai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sai.HistoricalDataFetcher.Candle;

public class HCBTestResults {

	public static void main(String[] args) throws IOException {
		String tokensPath = "src/com/sai/tokens_equity.json";
		String tokensJson = new String(Files.readAllBytes(Paths.get(tokensPath)));
		JSONArray tokensArray = new JSONArray(tokensJson);

		Map<String, String> tokenMap = new HashMap<>();
		for (int i = 0; i < tokensArray.length(); i++) {
			JSONObject entry = tokensArray.getJSONObject(i);
			tokenMap.put(entry.getString("symbol").trim(), String.valueOf(entry.get("token")));
		}

		String csvPath = "src/com/sai/records_hcb.csv";
		List<String> lines = Files.readAllLines(Paths.get(csvPath));

		System.out.println(
				"Stock|Date|BuyPrice|sl|tsl|target|high|low|status|tslStatus|triggerTime|exitTime|pattern|strategy");

		DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);
		// based on strategy sell or buy write two methods and validate status
		for (String line : lines) {
			String[] result = line.split(",");
			String date = result[0].trim();
			String symbol = result[1].trim();
			String strategy = result[2].trim();
			
			if (strategy.contains("SELL") || symbol.contains("ETF")
					|| symbol.contains("BEES")) {
				continue;
			}

			String token = tokenMap.getOrDefault(symbol, "NOT FOUND");

			if (token.equals("NOT FOUND")) {
				continue;
			}
			OffsetDateTime previousDay3PM = null;
			OffsetDateTime parsedDate = OffsetDateTime.parse(date);
			if (parsedDate.getDayOfWeek() == DayOfWeek.MONDAY) {
				previousDay3PM = parsedDate.minusDays(4).with(LocalTime.of(15, 0));
			} else {
				previousDay3PM = parsedDate.minusDays(4).with(LocalTime.of(15, 0));
			}
			LocalDateTime fromDt = previousDay3PM.toLocalDateTime();
			LocalDateTime toDt = fromDt.plusDays(30);
			String fromDate = fromDt.format(apiFmt);
			String toDate = toDt.format(apiFmt);

			try {
				HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
				List<Candle> candles = fetcher.fetchCandleData(HistoricalDataFetcher.Exchange.NSE, token,
						HistoricalDataFetcher.Interval.ONE_MINUTE, fromDate, toDate);
				if (strategy.equals("Hourly Support Buy") || strategy.equals("HCB Buy")) {
					analyzeLongTrade(symbol, token, date, candles, strategy);
				}
				// else if (strategy.equals("Hourly Resistence Sell") || strategy.equals("HCB
				// Sell")) {
				// analyzeShortTrade(symbol, token, date, candles, strategy);
				// }

				fetcher.close();
			} catch (Exception e) {
				System.err.println("Error fetching candles for " + symbol + ": " + e.getMessage());
			}
		}
	}

	private static void analyzeLongTrade(String stock, String token, String date, List<Candle> candles,
			String strategy) {
		List<Candle> first3Candles = get3FifteenMinCandles(candles, date);
		Candle resultCandle = first3Candles.get(2);
		double buyPrice = resultCandle.high;
		double sl = resultCandle.low;
		double tsl = sl;
		double high = 0;
		double target = (1.1 * buyPrice);
		String triggerredTime = "", exitTime = "";
		OffsetDateTime resultDateTime = OffsetDateTime.parse(date);
		OffsetDateTime analysisStartCandle = resultDateTime.plusMinutes(15);
		LocalDate candleDate = resultDateTime.toLocalDate();
		String pattern = CandlePatternUtility.detectOpeningCandlePatterns(first3Candles, candleDate);
		String status = "Watching";
		String tslStatus = "Not hit";
		double watchPoints = buyPrice - sl;
		OffsetDateTime cutOffTime = resultDateTime.plusMinutes(60);

		for (int i = 0; i < candles.size(); i++) {
			Candle currentCandle = candles.get(i);
			if (currentCandle.timestamp.isEmpty()) {
				continue;
			}
			OffsetDateTime currentCandleParsedTime = OffsetDateTime.parse(currentCandle.timestamp);

			if (currentCandleParsedTime.isEqual(analysisStartCandle)
					|| currentCandleParsedTime.isAfter(analysisStartCandle)) {
				if (status.equals("Watching")) {
					if (currentCandle.high > buyPrice) {
						status = "Open";
						high = currentCandle.high;
						triggerredTime = currentCandle.timestamp;
					} else if (currentCandle.low < sl || currentCandleParsedTime.isEqual(cutOffTime)
							|| currentCandleParsedTime.isAfter(cutOffTime)) {
						status = "Not Triggerred";
					}
				} else if (status.equals("Open")) {
					if (currentCandle.high > high) {
						high = currentCandle.high;
						tsl = sl + high - buyPrice; // As price moves up adjust tsl with the same distance from buy
													// price as sl
						/*
						 * if (high >= buyPrice + (watchPoints / 2)) tsl = buyPrice; if (high >=
						 * buyPrice + (watchPoints * 0.75)) tsl = buyPrice + (watchPoints / 2);
						 */
						if (high >= buyPrice + (watchPoints / 2))
							tsl = buyPrice;
						if (high >= buyPrice + (watchPoints * 0.75))
							tsl = buyPrice + (watchPoints / 2);
						if (high >= buyPrice + watchPoints && high <= buyPrice + (watchPoints * 1.25))
							tsl = buyPrice + (watchPoints * 0.75);
						if (high >= buyPrice + (watchPoints * 1.25) && high < buyPrice +(watchPoints * 2))
							tsl = buyPrice + watchPoints;
						if(high >= buyPrice +(watchPoints * 2))
							tsl = high - watchPoints;
						if (high >= target) {
							status = "Profit";
							tslStatus = "Not hit";
							exitTime = currentCandle.timestamp;
							break;
						}
					} else if (currentCandle.low < tsl) {//
						if (tsl > buyPrice) {
							status = "Profit";
						} else if (tsl < buyPrice) {
							status = "Loss";
						} else {
							status = "Neutral";
						}
						tslStatus = "SL hit";
						exitTime = currentCandle.timestamp;
						break;
					}
				}
			}

		}

		if (status.equals("Profit") || status.equals("Loss")) {
			System.out.println(stock + "|" + date + "|" + buyPrice + "|" + sl + "|" + tsl + "|" + target + "|" + high
					+ "|0|" + status + "|" + tslStatus + "|" + triggerredTime + "|" + exitTime + "|" + pattern + "|"
					+ strategy);
		}

	}

	private static void analyzeShortTrade(String stock, String token, String date, List<Candle> candles,
			String strategy) {
		List<Candle> first3Candles = get3FifteenMinCandles(candles, date);
		Candle resultCandle = first3Candles.get(2);
		double buyPrice = resultCandle.low;
		double sl = resultCandle.high;
		double tsl = sl;
		double low = 0;
		double target = (2 * buyPrice) - sl;
		String triggerredTime = "", exitTime = "";
		OffsetDateTime resultDateTime = OffsetDateTime.parse(date);
		OffsetDateTime analysisStartCandle = resultDateTime.plusMinutes(15);
		LocalDate candleDate = resultDateTime.toLocalDate();
		String pattern = CandlePatternUtility.detectBerishPatterns(first3Candles, candleDate);
		String status = "Watching";
		String tslStatus = "Not hit";
		double watchPoints = sl - buyPrice;
		OffsetDateTime cutOffTime = resultDateTime.plusMinutes(60);

		for (int i = 0; i < candles.size(); i++) {
			Candle currentCandle = candles.get(i);
			if (currentCandle.timestamp.isEmpty()) {
				continue;
			}
			OffsetDateTime currentCandleParsedTime = OffsetDateTime.parse(currentCandle.timestamp);

			if (currentCandleParsedTime.isEqual(analysisStartCandle)
					|| currentCandleParsedTime.isAfter(analysisStartCandle)) {
				if (status.equals("Watching")) {
					if (currentCandle.low < buyPrice) {
						status = "Open";
						low = currentCandle.low;
						triggerredTime = currentCandle.timestamp;
					} else if (currentCandle.high > sl || currentCandleParsedTime.isEqual(cutOffTime)
							|| currentCandleParsedTime.isAfter(cutOffTime)) {
						status = "Not Triggerred";
					}
				} else if (status.equals("Open")) {
					if (currentCandle.low > low) {
						low = currentCandle.low;
						if (low <= buyPrice - (watchPoints / 2))
							tsl = buyPrice;
						if (low <= buyPrice - (watchPoints * 0.75))
							tsl = buyPrice - (watchPoints / 2);
						if (low <= target) {
							status = "Profit";
							tslStatus = "Not hit";
							exitTime = currentCandle.timestamp;
							break;
						}
					} else if (currentCandle.high > tsl) {
						if (tsl < buyPrice) {
							status = "Profit";
						} else if (tsl > buyPrice) {
							status = "Loss";
						} else {
							status = "Neutral";
						}
						tslStatus = "SL hit";
						exitTime = currentCandle.timestamp;
						break;
					}
				}
			}
		}

		System.out.println(stock + "|" + date + "|" + buyPrice + "|" + sl + "|" + tsl + "|" + target + "|0|" + low + "|"
				+ status + "|" + tslStatus + "|" + triggerredTime + "|" + exitTime + "|" + pattern + "|" + strategy);
	}

	private static List<Candle> get3FifteenMinCandles(List<Candle> candles, String date) {
		List<Candle> threeCandles = new ArrayList<Candle>();
		OffsetDateTime parsedDate = OffsetDateTime.parse(date);
		boolean flag915 = date.contains("T09:15:0");
		boolean flag930 = date.contains("T09:30:0");
		OffsetDateTime candle0DateTime = null;
		OffsetDateTime candle1DateTime = null;
		if (flag915) {
			if (parsedDate.getDayOfWeek() == DayOfWeek.MONDAY) {
				candle0DateTime = parsedDate.minusDays(4).with(LocalTime.of(15, 0));
				candle1DateTime = parsedDate.minusDays(4).with(LocalTime.of(15, 15));
			} else {
				candle0DateTime = parsedDate.minusDays(4).with(LocalTime.of(15, 0));
				candle1DateTime = parsedDate.minusDays(4).with(LocalTime.of(15, 15));
			}

		} else if (flag930) {
			if (parsedDate.getDayOfWeek() == DayOfWeek.MONDAY) {
				candle0DateTime = parsedDate.minusDays(4).with(LocalTime.of(15, 15));
			} else {
				candle0DateTime = parsedDate.minusDays(4).with(LocalTime.of(15, 15));
			}
			candle1DateTime = parsedDate.minusMinutes(15);
		} else {
			candle0DateTime = parsedDate.minusMinutes(30);
			candle1DateTime = parsedDate.minusMinutes(15);
		}

		threeCandles.add(build15MinCandle(candles, candle0DateTime));
		threeCandles.add(build15MinCandle(candles, candle1DateTime));
		threeCandles.add(build15MinCandle(candles, parsedDate));
		return threeCandles;
	}

	private static Candle build15MinCandle(List<Candle> candles, OffsetDateTime date) {
		double open = 0, close = 0, high = 0, low = 0;
		int volume = 0;
		String candleDate15Min = "";
		OffsetDateTime nextCandleTime = date.plusMinutes(15);
		for (int i = 0; i < candles.size(); i++) {
			Candle candle = candles.get(i);
			OffsetDateTime candleDate = OffsetDateTime.parse(candle.timestamp);
			if (candleDate.isEqual(nextCandleTime)) {
				break;
			}
			if (candleDate.isEqual(date)) {
				open = candle.open;
				high = candle.high;
				low = candle.low;
				candleDate15Min = candle.timestamp;
				close = candle.close;
				volume += candle.volume;
			} else if (candleDate.isAfter(date) && candleDate.isBefore(nextCandleTime)) {
				if (candleDate15Min.isEmpty()) {
					open = candle.open;
					low = candle.low;
					candleDate15Min = candle.timestamp;
				}
				if (candle.high > high)
					high = candle.high;
				if (candle.low < low)
					low = candle.low;
				close = candle.close;
				volume += candle.volume;
			}

		}

		return new Candle(candleDate15Min, open, high, low, close, volume);
	}

}
