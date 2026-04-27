import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AnalyzeSaiAlgoResults {

	public static void main(String[] args) {
		// Load results.csv from resources (classpath root)
		try (var in = AnalyzeSaiAlgoResults.class.getResourceAsStream("/results.csv")) {
			if (in == null) {
				System.err.println("resources/results.csv not found on classpath");
				return;
			}
			var br = new java.io.BufferedReader(new java.io.InputStreamReader(in));
			java.util.Map<String, java.util.List<SaiScanResult>> grouped = new java.util.LinkedHashMap<>();
			String line;
			boolean first = true;
			while ((line = br.readLine()) != null) {
				if (first) {
					first = false;
					// skip header if it looks like one
					if (line.toLowerCase().contains("date") && line.toLowerCase().contains("stock")) continue;
				}
				line = line.trim();
				if (line.isEmpty()) continue;
				// naive CSV split on comma — assumes fields don't contain commas
				String[] parts = line.split(",");
				if (parts.length < 4) {
					System.err.println("Skipping malformed line: " + line);
					continue;
				}
				String dateStr = parts[0].trim();
				String order = parts[1].trim();
				String stock = parts[2].trim();
				String priceStr = parts[3].trim();
				LocalDateTime date;
				try {
					date = LocalDateTime.parse(dateStr);
				} catch (Exception ex) {
					// fallback: try yyyy/MM/dd
					date = LocalDateTime.parse(dateStr.replace('/', '-'));
				}
				double price = 0.0;
				try { price = Double.parseDouble(priceStr); } catch (Exception e) { /* leave 0.0 */ }
				SaiScanResult r = new SaiScanResult();
				r.date = date;
				r.stock = stock;
				r.order = order;
				r.price = price;
				grouped.computeIfAbsent(stock, k -> new java.util.ArrayList<>()).add(r);
			}
			 List<Order> orders = new ArrayList<>();
			System.out.println("stock,buyDateTime,buyPrice,sellDateTime,sellPrice,PnL");
			// Example: print group sizes
			for (var e : grouped.entrySet()) {
				List<SaiScanResult> results = e.getValue();
				for(int i =0; i < results.size(); ) {
					SaiScanResult r = results.get(i);
					if(i==0 && r.order.equalsIgnoreCase("SELL")){
						i++;
						continue; // skip initial SELL if it's the first record for the stock
					}  else if(i==results.size()-1 && r.order.equalsIgnoreCase("BUY")){
						i++;
						//System.out.println("Open Order : "+r.stock+" BUY on "+r.date+" at "+r.price);
						continue;
					}

					SaiScanResult rNext = results.get(i+1);
					Order order = new Order(r.stock, r.date, r.price, rNext.date, rNext.price);
					orders.add(order);
					System.out.println(order);
					i = i+2; // move to the next pair
				}
				
				//System.out.println(e.getKey() + " -> " + e.getValue().size() + " rows");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

class Order {
	String stock;
	LocalDateTime buyDate;
	double buyPrice;
	LocalDateTime sellDate;
	double sellPrice;

	public Order(String stock, LocalDateTime buyDate, double buyPrice, LocalDateTime sellDate, double sellPrice) {
		this.stock = stock;
		this.buyDate = buyDate;
		this.buyPrice = buyPrice;
		this.sellDate = sellDate;
		this.sellPrice = sellPrice;
	}

	public void setSell(LocalDateTime sellDate, double sellPrice) {
		this.sellDate = sellDate;
		this.sellPrice = sellPrice;
	}

	public boolean isClosed() {
		return sellDate != null;
	}

	public double getProfit() {
		if (!isClosed()) return 0.0;
		return sellPrice - buyPrice;
	}

	@Override
	public String toString() {
		return String.format("%s,%s,%.2f,%s,%.2f,%.2f",
				stock, buyDate, buyPrice,
				sellDate != null ? sellDate : "OPEN", sellPrice,
				getProfit());
	}
}

class SaiScanResult {
	LocalDateTime date;
	String stock;
	String order;
	double price;
}
