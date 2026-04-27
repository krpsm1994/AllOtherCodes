package com.sai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sai.HistoricalDataFetcher.Candle;

public class HCBFutureBuySell {

	public static void main(String[] args) throws IOException {
		String tokensPath = "src/com/sai/tokens.json";
		String tokensJson = new String(Files.readAllBytes(Paths.get(tokensPath)));
		JSONArray tokensArray = new JSONArray(tokensJson);

		Map<String, String> tokenMap = new HashMap<>();
		for (int i = 0; i < tokensArray.length(); i++) {
			JSONObject entry = tokensArray.getJSONObject(i);
			tokenMap.put(entry.getString("symbol").trim(), String.valueOf(entry.get("token")));
		}
		System.out.println("Loaded " + tokenMap.size() + " tokens.");
		System.out.println("Date,Symbol,Strategy");
		//DateTimeFormatter apiFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);
		String fromDate = "2025-01-01 09:15";
		String toDate = "2026-03-20 15:15";
		// String stock = "TATASTEEL";
		for (String stock : tokenMap.keySet()) {
			String token = tokenMap.get(stock);
			HistoricalDataFetcher fetcher = new HistoricalDataFetcher();
			try {
				List<HistoricalDataFetcher.Candle> candles = fetcher.fetchCandleData(HistoricalDataFetcher.Exchange.NSE,
						token, HistoricalDataFetcher.Interval.FIFTEEN_MINUTE, fromDate, toDate);
				checkHCBFutureBuyRules(stock, token, candles);
				//checkHCBFutureSellRules(stock, token, candles);
				checkHourlySupportBuyRules(stock, token, candles);
				//checkHourlyResistanceSellRules(stock, token, candles);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	private static void checkHourlyResistanceSellRules(String stock, String token, List<Candle> candles) {
		for (int i = 30; i < candles.size(); i++) {
			HistoricalDataFetcher.Candle currentCandle = candles.get(i);
			HistoricalDataFetcher.Candle previousCandle = candles.get(i - 1);
			if (currentCandle.low > 50 && currentCandle.volume > 100000 && currentCandle.close < currentCandle.open
					&& currentCandle.close < previousCandle.low && (currentCandle.high - currentCandle.low) < (previousCandle.close * 0.03)) {
				double hourly10Sma = getHourlySMA10(candles.subList(i - 30, i + 1));
				if(currentCandle.close < hourly10Sma &&currentCandle.open < hourly10Sma &&currentCandle.high > hourly10Sma) {
					double volume20Sma = getVolumeSMA(candles.subList(i-20,i+1),20);
					if(currentCandle.volume >= volume20Sma *3) {
						System.out.println(currentCandle.timestamp+","+stock+",Hourly Resistence Sell");
					}
				}
			}
		}
	}

	private static double getHourlySMA10(List<Candle> candles) {
		String[] arr = { "15:15", "15:00", "14:00", "13:00", "14:00", "12:00", "11:00", "10:00" };
		ArrayList<String> times = new ArrayList<>(Arrays.asList(arr));
		ArrayList<Double> closes = new ArrayList<>();
		// System.out.println("candleTime : " + candles.get(candles.size() -
		// 1).timestamp);
		closes.add(candles.get(candles.size() - 1).close);
		int count = 1;
		for (int i = candles.size() - 1; count < 10 && i > -1; i--) {
			String timeStampTime = candles.get(i).timestamp.substring(11, 16);
			if (times.contains(timeStampTime)) {
				closes.add(candles.get(i).close);
				count++;
			}
		}
		return (closes.stream().mapToDouble(Double::doubleValue).sum()) / 10;
	}

	private static double getDailySMA(List<Candle> candles, int length) {
		ArrayList<Double> closes = new ArrayList<>();
		// System.out.println("candleTime : " + candles.get(candles.size() -
		// 1).timestamp);
		closes.add(candles.get(candles.size() - 1).close);
		int count = 1;
		for (int i = candles.size() - 1; count < length && i > -1; i--) {
			String timeStampTime = candles.get(i).timestamp.substring(11, 16);
			if (timeStampTime.equals("15:15")) {
				closes.add(candles.get(i).close);
				count++;
			}
		}
		// System.out.println(closes.toString());
		return (closes.stream().mapToDouble(Double::doubleValue).sum()) / length;
	}

	private static void checkHourlySupportBuyRules(String stock, String token, List<Candle> candles) {
		for (int i = 30; i < candles.size(); i++) {
			HistoricalDataFetcher.Candle currentCandle = candles.get(i);
			HistoricalDataFetcher.Candle previousCandle = candles.get(i - 1);
			if (currentCandle.low > 50 && currentCandle.volume > 100000 && currentCandle.close > currentCandle.open
					&& currentCandle.close > previousCandle.high && (currentCandle.high - currentCandle.low) < (previousCandle.close * 0.03)) {
				double hourly10Sma = getHourlySMA10(candles.subList(i - 30, i + 1));
				if(currentCandle.close >hourly10Sma &&currentCandle.open >hourly10Sma &&currentCandle.low < hourly10Sma) {
					double volume20Sma = getVolumeSMA(candles.subList(i-20,i+1),20);
					if(currentCandle.volume >= volume20Sma *3) {
						System.out.println(currentCandle.timestamp+","+stock+",Hourly Support Buy");
					}
				}
			}
		}
	}
	
	private static double getVolumeSMA(List<Candle> candles, int length) {
		 double totalVolume = 0.0d;
		 for(Candle candle: candles) {
			 totalVolume += candle.volume;
		 }
		return totalVolume/length;
	}

	private static void checkHCBFutureSellRules(String stock, String token, List<Candle> candles) {
		for (int i = 501; i < candles.size(); i++) {
			HistoricalDataFetcher.Candle currentCandle = candles.get(i);
			HistoricalDataFetcher.Candle previousCandle = candles.get(i - 1);
			if (currentCandle.timestamp.contains("T09:15:0")) {
				if (currentCandle.low > 50 && currentCandle.volume > 100000 && currentCandle.close < currentCandle.open
						&& currentCandle.close < previousCandle.low
						&& (currentCandle.high - currentCandle.low) < (previousCandle.close * 0.03)) {
					// calculate hourly SMA 10 close
					double hourly10Sma = getHourlySMA10(candles.subList(i - 30, i + 1));
					if (currentCandle.close < hourly10Sma) {
						// calculate daily sma 10 close
						double daily10Sma = getDailySMA(candles.subList(i - 300, i + 1), 10);
						if (currentCandle.close < daily10Sma) {
							// calculate daily sma 20 close
							double daily20Sma = getDailySMA(candles.subList(0, i + 1), 20);
							if (currentCandle.close < daily10Sma) {
								System.out.println(currentCandle.timestamp+","+stock+",HCB Sell");
							}
						}
					}
				}
			}
		}
	}

	private static void checkHCBFutureBuyRules(String stock, String token, List<Candle> candles) {
		for (int i = 501; i < candles.size(); i++) {
			HistoricalDataFetcher.Candle currentCandle = candles.get(i);
			HistoricalDataFetcher.Candle previousCandle = candles.get(i - 1);
			if (currentCandle.timestamp.contains("T09:15:0")) {
				if (currentCandle.low > 50 && currentCandle.volume > 100000 && currentCandle.close > currentCandle.open
						&& currentCandle.close > previousCandle.high
						&& (currentCandle.high - currentCandle.low) < (previousCandle.close * 0.03)) {
					// calculate hourly SMA 10 close
					double hourly10Sma = getHourlySMA10(candles.subList(i - 30, i + 1));
					if (currentCandle.close > hourly10Sma) {
						// calculate daily sma 10 close
						double daily10Sma = getDailySMA(candles.subList(i - 300, i + 1), 10);
						if (currentCandle.close > daily10Sma) {
							// calculate daily sma 20 close
							double daily20Sma = getDailySMA(candles.subList(0, i + 1), 20);
							if (currentCandle.close > daily20Sma) {
								System.out.println(currentCandle.timestamp+","+stock+",HCB Buy");
							}
						}
					}
				}
			}
		}
	}

}
