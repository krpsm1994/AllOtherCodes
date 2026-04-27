package com.sai;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AnalyzeInReality {
	
	static class TradeSignal {
		String stock;
        OffsetDateTime triggerTime;
        OffsetDateTime exitTime;
        double buy;
        double sell;
        String status;
        String strategy;
		public TradeSignal(String stock, OffsetDateTime triggerTime, OffsetDateTime exitTime,
				double buy, double sell, String status, String strategy) {
			super();
			this.stock = stock;
			this.triggerTime = triggerTime;
			this.exitTime = exitTime;
			this.buy = buy;
			this.sell = sell;
			this.status = status;
			this.strategy = strategy;
		}
	}

	public static void main(String[] args) throws IOException {
		String csvPath = "src/com/sai/BacktestResults.csv";
		List<TradeSignal> signals = loadSignals(csvPath);
		
		if (signals.isEmpty()) {
            System.out.println("No valid data found or file is missing.");
            return;
        }
		
		// Sort chronologically by trigger time
        signals.sort(Comparator.comparing(s -> s.triggerTime));
		
		double initialCapital = 100000;
		double currentCapital = initialCapital;
		double brokerage = 60;
		double amountPerTrade = 20000;
		double maxTrades = Math.floor(initialCapital/amountPerTrade);
		
		List<TradeSignal> trades = new ArrayList<>();
		List<TradeSignal> realTrades = new ArrayList<>();
		
		for(TradeSignal signal : signals) {
			if(trades.size() < maxTrades) {
				signal.status = "Taken";
				if(signal.buy > 0) {
					trades.add(signal);
					trades.sort(Comparator.comparing(s -> s.exitTime));
				}
			} else {
				if( trades.size() > 0 && signal.buy > 0 && signal.buy < 2500 && trades.get(0).exitTime.isBefore(signal.triggerTime)) {
					realTrades.add(trades.get(0));
					currentCapital = currentCapital + getPnl(trades.get(0), amountPerTrade) - brokerage;
					maxTrades = Math.floor(currentCapital/amountPerTrade);
					trades.remove(0);
					trades.add(signal);
					trades.sort(Comparator.comparing(s -> s.exitTime));
				}
			}
		}
		
		for(TradeSignal trade : trades) {
			realTrades.add(trade);
		}
		
		System.out.println("Total Signals : "+signals.size() + " Total Trades Taken : " +realTrades.size());
		System.out.println("Initial Capital : "+initialCapital + " current Capital : " +currentCapital + " Total Brokerage : " + (brokerage * realTrades.size()));
		
		System.out.println("stock|buy|sell|shares|pnl|pnl%|strategy|TriggerTime|exitTime");
		for(TradeSignal trade : realTrades) {
			int shareCount = (int) Math.floor(amountPerTrade/trade.buy);
			double pnl = (trade.sell - trade.buy) * shareCount;
			double pnlPercent = (pnl/amountPerTrade)*100;
			System.out.println(trade.stock+"|"+trade.buy+"|"+trade.sell+"|"+shareCount+"|"+pnl+"|"+pnlPercent+"|"+trade.strategy+"|"+trade.triggerTime+"|"+trade.exitTime);
		}
		
	}
	
	private static double getPnl(TradeSignal trade, double amountPerTrade) {
		double pnl =0d;
		int shareCount = (int) Math.floor(amountPerTrade/trade.buy);
		pnl = (trade.sell - trade.buy) * shareCount;
		return pnl;
	}
	
	private static List<TradeSignal> loadSignals(String filePath) {
        List<TradeSignal> signals = new ArrayList<TradeSignal>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] cols = line.split("\\|");
                if (cols.length < 13) continue;

                String stock = cols[0];
                String tslStatus = cols[10];
                String triggerTimeStr = cols[11];
                String exitTimeStr = cols[12];
                Double buy = Double.parseDouble(cols[2]);
                Double sell = 0d;
                String currentstatus = "Not Evaluated";
                String strategy = cols[14];
                try {
                    OffsetDateTime triggerTime = OffsetDateTime.parse(triggerTimeStr);
                    OffsetDateTime exitTime = OffsetDateTime.parse(exitTimeStr);
                    
                    if(tslStatus.equals("Not hit")) {
                    	sell =Double.parseDouble(cols[5]);
                    } else {
                    	sell =Double.parseDouble(cols[4]);
                    }
                    
                    signals.add(new TradeSignal(stock, triggerTime, exitTime,buy,sell,currentstatus,strategy));
                } catch (DateTimeParseException e) {
                    
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return signals;
    }
	
	private static double getReturnMapping(String status, String tslStatus) {
        status = status.toLowerCase().trim();
        tslStatus = tslStatus.toLowerCase().trim();

        if (status.contains("profit") && tslStatus.contains("not hit")) return 0.10;
        if (status.contains("profit") && tslStatus.contains("sl hit")) return 0.05;
        if (status.contains("loss") && tslStatus.contains("sl hit")) return -0.016;
        return 0.0; // Neutral returns 0%
    }

}
