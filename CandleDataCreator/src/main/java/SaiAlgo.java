import java.util.*;

public class SaiAlgo {
	// === INPUTS ===
	String res = "15";
	boolean useRes = true;
	int intRes = 8;
	String basisType = "TEMA";
	int basisLen = 2;
	int offsetSigma = 5;
	double offsetALMA = 0.85;
	boolean scolor = false;
	int delayOffset = 0;
	String tradeType = "BOTH";
	boolean h = false;
	int swing_length = 10;
	int history_of_demand_to_keep = 20;
	double box_width = 2.5;
	boolean show_zigzag = false;
	boolean show_price_action_labels = false;
	// Colors and visual settings are ignored or replaced with logs

	// Data arrays
	Deque<Double> swing_high_values = new ArrayDeque<>(Arrays.asList(0.0,0.0,0.0,0.0,0.0));
	Deque<Double> swing_low_values = new ArrayDeque<>(Arrays.asList(0.0,0.0,0.0,0.0,0.0));
	Deque<Integer> swing_high_bns = new ArrayDeque<>(Arrays.asList(0,0,0,0,0));
	Deque<Integer> swing_low_bns = new ArrayDeque<>(Arrays.asList(0,0,0,0,0));

	// Supply/Demand boxes (simulate with lists)
	List<Box> current_supply_box = new ArrayList<>();
	List<Box> current_demand_box = new ArrayList<>();
	List<Box> current_supply_poi = new ArrayList<>();
	List<Box> current_demand_poi = new ArrayList<>();
	List<Box> supply_bos = new ArrayList<>();
	List<Box> demand_bos = new ArrayList<>();

	// Bar data
	public static List<CandleBar> bars = new ArrayList<>();

	// Multi-timeframe settings
	int mtfWindowSize = 0; // number of bars per bucket; 0 = disabled
	boolean useMtf = false;

	// ATR calculation (placeholder) using CandleBar
	double atr(List<CandleBar> barsList, int period, int idx) {
		if (idx < period) return 0.0;
		double sum = 0.0;
		for (int k = idx - period + 1; k <= idx; k++) {
			CandleBar b = barsList.get(k);
			CandleBar prev = barsList.get(k - 1);
			double tr = Math.max(b.getHigh() - b.getLow(), Math.max(Math.abs(b.getHigh() - prev.getClose()), Math.abs(b.getLow() - prev.getClose())));
			sum += tr;
		}
		return sum / period;
	}

	// Add new and remove last in array
	<T> void arrayAddPop(Deque<T> array, T newValue) {
		array.addFirst(newValue);
		if (array.size() > 5) array.removeLast();
	}

	// Main logic (simulate bar-by-bar)
	public void run() {
		// If configured, compute N-bar MTF values once before processing
		if (useMtf && mtfWindowSize > 0) {
			calculateNBarSecurityForCandleBars(bars, mtfWindowSize);
			System.out.println("MTF: calculated " + mtfWindowSize + "-bar buckets (CandleBar)");
		}
		for (int i = 1; i < bars.size(); i++) {
			double atrValue = atr(bars, 50, i);
			// Calculate swing highs/lows (placeholder logic)
			Double swing_high = null, swing_low = null;
			if (i >= 2 * swing_length) {
				boolean isHigh = true, isLow = true;
				for (int j = i - swing_length; j <= i + swing_length && j < bars.size(); j++) {
					if (j < 0 || j == i) continue;
					if (bars.get(i).getHigh() < bars.get(j).getHigh()) isHigh = false;
					if (bars.get(i).getLow() > bars.get(j).getLow()) isLow = false;
				}
				if (isHigh) swing_high = bars.get(i).getHigh();
				if (isLow) swing_low = bars.get(i).getLow();
			}
			// New swing high
			if (swing_high != null) {
				arrayAddPop(swing_high_values, swing_high);
				arrayAddPop(swing_high_bns, i - swing_length);
				if (show_price_action_labels) {
					System.out.println("Swing High Label: " + swing_high);
				}
				// Supply zone logic
				addSupplyDemandBox(swing_high_values, swing_high_bns, current_supply_box, current_supply_poi, 1, atrValue, i);
			} else if (swing_low != null) {
				arrayAddPop(swing_low_values, swing_low);
				arrayAddPop(swing_low_bns, i - swing_length);
				if (show_price_action_labels) {
					System.out.println("Swing Low Label: " + swing_low);
				}
				// Demand zone logic
				addSupplyDemandBox(swing_low_values, swing_low_bns, current_demand_box, current_demand_poi, -1, atrValue, i);
			}
			// BOS logic
			checkBOS(current_supply_box, supply_bos, current_supply_poi, 1, i);
			checkBOS(current_demand_box, demand_bos, current_demand_poi, -1, i);
			// Extend box endpoints
			extendBoxEndpoint(current_supply_box, i);
			extendBoxEndpoint(current_demand_box, i);

			// --- Entry/Exit/TP/SL logic (simplified, logs only) ---
			// Example: Use moving average cross for entry/exit triggers
			// For demonstration, use simple MA
			double maClose = simpleMA(bars, basisLen, i);
			double maOpen = simpleMAOpen(bars, basisLen, i);
			boolean leTrigger = (i > 0 && bars.get(i-1).getClose() < bars.get(i-1).getOpen() && bars.get(i).getClose() > bars.get(i).getOpen());
			boolean seTrigger = (i > 0 && bars.get(i-1).getClose() > bars.get(i-1).getOpen() && bars.get(i).getClose() < bars.get(i).getOpen());
			// Entry/exit state
			boolean inLong = false, inShort = false;
			double entryPrice = 0.0, slLine = 0.0, tp1Line = 0.0, tp2Line = 0.0, tp3Line = 0.0;
			// Simulate position state (for demonstration, not persistent)
			if (leTrigger) {
				inLong = true;
				entryPrice = bars.get(i).getClose();
				slLine = entryPrice - (entryPrice * 0.005); // 0.5% SL
				tp1Line = entryPrice + (entryPrice * 0.01); // 1% TP
				tp2Line = entryPrice + (entryPrice * 0.015); // 1.5% TP
				tp3Line = entryPrice + (entryPrice * 0.02); // 2% TP
				System.out.println("Long Entry at " + entryPrice + " (bar " + i + ")");
			}
			if (seTrigger) {
				inShort = true;
				entryPrice = bars.get(i).getClose();
				slLine = entryPrice + (entryPrice * 0.005); // 0.5% SL
				tp1Line = entryPrice - (entryPrice * 0.01); // 1% TP
				tp2Line = entryPrice - (entryPrice * 0.015); // 1.5% TP
				tp3Line = entryPrice - (entryPrice * 0.02); // 2% TP
				System.out.println("Short Entry at " + entryPrice + " (bar " + i + ")");
			}
			// Take profit/stop loss checks (logs only)
			if (inLong) {
				if (bars.get(i).getHigh() >= tp1Line) System.out.println("Long TP1 hit at " + tp1Line + " (bar " + i + ")");
				if (bars.get(i).getHigh() >= tp2Line) System.out.println("Long TP2 hit at " + tp2Line + " (bar " + i + ")");
				if (bars.get(i).getHigh() >= tp3Line) System.out.println("Long TP3 hit at " + tp3Line + " (bar " + i + ")");
				if (bars.get(i).getLow() <= slLine) System.out.println("Long SL hit at " + slLine + " (bar " + i + ")");
			}
			if (inShort) {
				if (bars.get(i).getLow() <= tp1Line) System.out.println("Short TP1 hit at " + tp1Line + " (bar " + i + ")");
				if (bars.get(i).getLow() <= tp2Line) System.out.println("Short TP2 hit at " + tp2Line + " (bar " + i + ")");
				if (bars.get(i).getLow() <= tp3Line) System.out.println("Short TP3 hit at " + tp3Line + " (bar " + i + ")");
				if (bars.get(i).getHigh() >= slLine) System.out.println("Short SL hit at " + slLine + " (bar " + i + ")");
			}
			// Plot/visual logs (include mtf value if present)
			double mtf = bars.get(i).getMtfValue();
			System.out.println("Plot: TP1Line=" + tp1Line + ", EntryLine=" + entryPrice + ", SLLine=" + slLine + ", MTF=" + mtf);
		}
	}

	// Internal helper to compute N-bar MTF using our CandleBar list
	void calculateNBarSecurityForCandleBars(List<CandleBar> barsList, int windowSize) {
		if (windowSize <= 0) windowSize = 10;
		if (barsList == null || barsList.isEmpty()) return;

		int n = barsList.size();
		for (int i = 0; i < n; i++) {
			int bucketStart = (i / windowSize) * windowSize;
			int bucketEnd = bucketStart + windowSize - 1;
			if (bucketEnd >= n) bucketEnd = n - 1;
			double mtfClose = barsList.get(bucketEnd).getClose();
			barsList.get(i).setMtfValue(mtfClose);
			System.out.printf("[SaiAlgo] Assigned %d-bar bucket [%d..%d] close=%.3f for bar idx=%d%n", windowSize, bucketStart, bucketEnd, mtfClose, i);
		}
	}

	// Simple moving average for close prices
	double simpleMA(List<CandleBar> barsArr, int len, int idx) {
		if (idx < len - 1) return 0.0;
		double sum = 0.0;
		for (int k = idx - len + 1; k <= idx; k++) sum += barsArr.get(k).getClose();
		return sum / len;
	}

	// Simple moving average for open prices
	double simpleMAOpen(List<CandleBar> barsArr, int len, int idx) {
		if (idx < len - 1) return 0.0;
		double sum = 0.0;
		for (int k = idx - len + 1; k <= idx; k++) sum += barsArr.get(k).getOpen();
		return sum / len;
	}

	// Add supply/demand box
	void addSupplyDemandBox(Deque<Double> value_array, Deque<Integer> bn_array, List<Box> box_array, List<Box> label_array, int box_type, double atrValue, int barIdx) {
		double atr_buffer = atrValue * (box_width / 10.0);
		int box_left = bn_array.peekFirst();
		int box_right = barIdx;
		double box_top = 0.0, box_bottom = 0.0, poi = 0.0;
		if (box_type == 1) {
			box_top = value_array.peekFirst();
			box_bottom = box_top - atr_buffer;
			poi = (box_top + box_bottom) / 2.0;
		} else if (box_type == -1) {
			box_bottom = value_array.peekFirst();
			box_top = box_bottom + atr_buffer;
			poi = (box_top + box_bottom) / 2.0;
		}
		boolean okay_to_draw = checkOverlapping(poi, box_array, atrValue);
		if (okay_to_draw) {
			if (box_array.size() >= history_of_demand_to_keep) box_array.remove(box_array.size() - 1);
			box_array.add(0, new Box(box_top, box_bottom, box_left, box_right, box_type == 1 ? "SUPPLY" : "DEMAND"));
			if (label_array.size() >= history_of_demand_to_keep) label_array.remove(label_array.size() - 1);
			label_array.add(0, new Box(poi, poi, box_left, box_right, "POI"));
			System.out.println((box_type == 1 ? "Supply" : "Demand") + " box drawn: top=" + box_top + ", bottom=" + box_bottom + ", left=" + box_left + ", right=" + box_right);
		}
	}

	// Check overlapping for supply/demand
	boolean checkOverlapping(double new_poi, List<Box> box_array, double atrValue) {
		double atr_threshold = atrValue * 2;
		for (Box b : box_array) {
			double poi = (b.top + b.bottom) / 2.0;
			double upper_boundary = poi + atr_threshold;
			double lower_boundary = poi - atr_threshold;
			if (new_poi >= lower_boundary && new_poi <= upper_boundary) {
				return false;
			}
		}
		return true;
	}

	// Change supply/demand to BOS if broken
	void checkBOS(List<Box> box_array, List<Box> bos_array, List<Box> label_array, int zone_type, int barIdx) {
		Iterator<Box> it = box_array.iterator();
		int idx = 0;
		while (it.hasNext()) {
			Box b = it.next();
			double level_to_break = (zone_type == 1) ? b.top : b.bottom;
			double price = bars.size() > barIdx ? bars.get(barIdx).getClose() : 0.0;
			boolean broken = (zone_type == 1) ? price >= level_to_break : price <= level_to_break;
			if (broken) {
				Box copied = new Box(b.top, b.bottom, b.left, barIdx, "BOS");
				bos_array.add(0, copied);
				System.out.println("BOS created at bar " + barIdx + ", level: " + level_to_break);
				it.remove();
				if (label_array.size() > idx) label_array.remove(idx);
			}
			idx++;
		}
	}

	// Extend box endpoint

	void extendBoxEndpoint(List<Box> box_array, int barIdx) {
		for (Box b : box_array) {
			b.right = barIdx + 100;
		}
	}

	// Setter for bars
	public void setBars(List<CandleBar> inputBars) {
		SaiAlgo.bars = inputBars;
	}

	// Demo runner
	public static void main(String[] args) {
		SaiAlgo algo = new SaiAlgo();
		// optional first arg: use MTF window size (number of bars). If provided and >0, MTF is enabled.
		if (args.length > 0) {
			try {
				int n = Integer.parseInt(args[0]);
				if (n > 0) { algo.mtfWindowSize = n; algo.useMtf = true; }
			} catch (Exception e) { /* ignore */ }
		}
		List<CandleBar> sample = new ArrayList<>();
		// create minimal sample data (timestamp null for brevity)
		for (int i = 0; i < 120; i++) {
			double price = 100 + Math.sin(i / 10.0) * 2 + i * 0.01;
			CandleBar cb = new CandleBar(null, price - 0.5, price + 0.5, price - 1.0, price, 1000);
			sample.add(cb);
		}
		algo.setBars(sample);
		algo.run();
	}
}

// Box class to simulate Pine Script box
class Box {
	double top, bottom;
	int left, right;
	String label;
	Box(double top, double bottom, int left, int right, String label) {
		this.top = top; this.bottom = bottom; this.left = left; this.right = right; this.label = label;
	}
}
