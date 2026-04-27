import java.util.*;
import java.time.*;
import java.time.format.*;

public class Get10MonthData {

	public static void main(String[] args) {
		List<Bar> bars = new ArrayList<>();

		double[][] ohlc = {
			{100.0, 110.0,  95.0, 1010.2},
			{105.0, 115.0, 100.0, 1016.1},
			{112.0, 120.0, 108.0, 1000.4},
			{118.0, 125.0, 113.0, 1000.00},
			{121.0, 130.0, 117.0, 967.1},
			{128.0, 135.0, 122.0, 908.4},
			{132.0, 140.0, 127.0, 922.4},
			{137.0, 145.0, 131.0, 939.1},
			{142.0, 150.0, 136.0, 940.2},
			{148.0, 155.0, 143.0, 927.7},
		};

		long baseTs = 1_700_000_000_000L; // arbitrary epoch ms start
		for (int i = 0; i < ohlc.length; i++) {
			Bar b = new Bar();
			b.open      = ohlc[i][0];
			b.high      = ohlc[i][1];
			b.low       = ohlc[i][2];
			b.close     = ohlc[i][3];
			b.timestamp = baseTs + (long) i * 30L * 24 * 60 * 60 * 1000; // ~1 month apart
			bars.add(b);
		}

		// Usage: java Get10MonthData <N> [mode]
		// mode: "bars" (bucket by number of candles) or "months" (calendar months). Default: bars
		int n = 10;
		String mode = "bars";
		if (args.length > 0) {
			try { n = Integer.parseInt(args[0]); } catch (Exception e) { /* ignore, keep default */ }
		}
		if (args.length > 1) {
			mode = args[1].toLowerCase();
		}

		if ("months".equals(mode)) {
			calculateNMonthSecurity(bars, n);
		} else {
			calculateNBarSecurity(bars, n);
		}

		System.out.println("\n--- Results ---");
		for (int i = 0; i < bars.size(); i++) {
			System.out.printf("Bar %2d | close=%.1f | mtfValue=%.1f%n",
				i + 1, bars.get(i).close, bars.get(i).mtfValue);
		}
	}
	
	public static void calculateNMonthSecurity(List<Bar> monthlyBars, int windowSize) {
		if (windowSize <= 0) windowSize = 10;
		if (monthlyBars == null || monthlyBars.isEmpty()) return;

		// Map YearMonth -> last close for that month from input data
		TreeMap<Integer, Double> monthClose = new TreeMap<>();
		for (Bar b : monthlyBars) {
			Instant inst = Instant.ofEpochMilli(b.timestamp);
			YearMonth ym = YearMonth.from(inst.atZone(ZoneId.of("Asia/Kolkata")));
			int idx = ym.getYear() * 12 + (ym.getMonthValue() - 1);
			monthClose.put(idx, b.close); // last write wins for same-month multiple bars
		}

		// Build continuous months range and filled closes (gaps_off semantics: fill missing months by carrying forward/backfilling)
		int startIdx = monthClose.firstKey();
		int endIdx = monthClose.lastKey();
		int totalMonths = endIdx - startIdx + 1;
		List<Integer> months = new ArrayList<>(totalMonths);
		List<Double> filledCloses = new ArrayList<>(totalMonths);

		// Prepare forward/backfill: find first available close
		Double prevClose = null;
		for (int m = startIdx; m <= endIdx; m++) {
			months.add(m);
			Double c = monthClose.get(m);
			if (c == null) {
				// if no previous close yet, look ahead to find next available to backfill
				if (prevClose == null) {
					// find next available
					Integer nextKey = monthClose.ceilingKey(m);
					c = nextKey != null ? monthClose.get(nextKey) : null;
				} else {
					c = prevClose; // carry forward
				}
			}
			filledCloses.add(c);
			prevClose = c;
		}

		// base for bucket alignment (use startIdx)
		int base = startIdx;

		// For each original monthly bar, compute its bucket and assign mtfValue from filled data
		for (Bar b : monthlyBars) {
			Instant inst = Instant.ofEpochMilli(b.timestamp);
			YearMonth ym = YearMonth.from(inst.atZone(ZoneId.of("Asia/Kolkata")));
			int cur = ym.getYear() * 12 + (ym.getMonthValue() - 1);
			int bucketStart = base + ((cur - base) / windowSize) * windowSize;
			int bucketEnd = bucketStart + windowSize - 1;

			// clamp bucketEnd to our filled range
			if (bucketEnd > endIdx) bucketEnd = endIdx;

			int listIdx = bucketEnd - startIdx; // index into filledCloses for bucketEnd
			if (listIdx < 0) listIdx = 0;
			if (listIdx >= filledCloses.size()) listIdx = filledCloses.size() - 1;

			double currentNMonthClose = filledCloses.get(listIdx);
			b.mtfValue = currentNMonthClose;
			System.out.printf("Assigned %d-month bucket [%d..%d] close=%.3f for bar ts=%d%n", windowSize, bucketStart, bucketEnd, currentNMonthClose, b.timestamp);
		}
	}

	// convenience wrapper keeping original name/behavior
	public static void calculate10MSecurity(List<Bar> monthlyBars) {
		calculateNMonthSecurity(monthlyBars, 10);
	}

	/**
	 * Bucketize by number of candles (bars). For each bar index i, find the bucket of size windowSize
	 * that contains it and take the close of the bucket's last bar as the mtf value (lookahead_on semantics).
	 */
	public static void calculateNBarSecurity(List<Bar> bars, int windowSize) {
		if (windowSize <= 0) windowSize = 10;
		if (bars == null || bars.isEmpty()) return;

		int n = bars.size();
		for (int i = 0; i < n; i++) {
			int bucketStart = (i / windowSize) * windowSize;
			int bucketEnd = bucketStart + windowSize - 1;
			if (bucketEnd >= n) bucketEnd = n - 1;
			double mtfClose = bars.get(bucketEnd).close;
			bars.get(i).mtfValue = mtfClose;
			System.out.printf("Assigned %d-bar bucket [%d..%d] close=%.3f for bar idx=%d ts=%d%n", windowSize, bucketStart, bucketEnd, mtfClose, i, bars.get(i).timestamp);
		}
	}

}

class Bar {
	double open, high, low, close;
    long timestamp;
    // This will store our calculated 10M value
    double mtfValue;
}
