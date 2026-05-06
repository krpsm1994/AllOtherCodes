package self.sai.stock.AlgoTrading.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import self.sai.stock.AlgoTrading.dto.CandleBar;
import self.sai.stock.AlgoTrading.dto.TEMASeries;
import self.sai.stock.AlgoTrading.entity.Instrument;
import self.sai.stock.AlgoTrading.entity.Trade;
import self.sai.stock.AlgoTrading.repository.InstrumentRepository;
import self.sai.stock.AlgoTrading.repository.TradeRepository;

@Service
public class AlgoScanService {
	private final InstrumentRepository instrumentRepository;
	private final TradeRepository tradeRepository;
	private final TradeMonitorService tradeMonitor;
	private final SettingService settingService;

	public InstrumentRepository getInstrumentRepository() {
		return instrumentRepository;
	}

	public AlgoScanService(InstrumentRepository instrumentRepository, TradeRepository tradeRepository,
			TradeMonitorService tradeMonitor, SettingService settingService) {
		this.instrumentRepository = instrumentRepository;
		this.tradeRepository = tradeRepository;
		this.tradeMonitor = tradeMonitor;
		this.settingService = settingService;
	}

	private static final Logger log = LoggerFactory.getLogger(AlgoScanService.class);

	public void runAlgoScan(Map<String, List<CandleBar>> candleMap, int numberOfCandles) {
		log.info("Algo scan analysis started....");
		log.info("candleMap Size :" + candleMap.size());
		for (String token : candleMap.keySet()) {
			Instrument inst = instrumentRepository.findById(token).orElse(null);
			if (inst == null) {
				log.warn("AlgoScanService Instrument not found for token {}", token);
			}

			List<CandleBar> candles = candleMap.get(token);

			// Skip if last raw candle is stale — prevents acting on 9:15 candle at 10:25
			CandleBar lastCandle = candles.get(candles.size() - 1);
			LocalDateTime staleThreshold = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusMinutes(30);
			if (lastCandle.getTimestamp().isBefore(staleThreshold)) {
				log.info("[AlgoScan] Skipping {} — last candle {} is stale (threshold {})",
						inst == null ? token : inst.getName(), lastCandle.getTimestamp(), staleThreshold);
				continue;
			}

			// Calculate TEMA series for Close and Open
			List<TEMASeries> htfTemaSeries = generateAltSeries(candles, 2);

			// generate signal series. This will be validated to check the signal generation
			List<TEMASeries> finalSecuritySeries = generateSignalSeries(htfTemaSeries, numberOfCandles);

			// Validate signal generation
			TEMASeries signalCandle = finalSecuritySeries.get(finalSecuritySeries.size() - 1);
			TEMASeries previousCandle = finalSecuritySeries.get(finalSecuritySeries.size() - 2);
			List<Trade> trades = tradeRepository.findAll();

			// Active statuses that should prevent duplicate insertion
			List<String> activeStatuses = List.of("WATCHING", "BUY PLACED", "OPEN", "SELL PLACED");
			boolean tradeAlreadyExists = trades.stream().anyMatch(
					t -> t.getToken().equals(token) && t.getStatus() != null && activeStatuses.contains(t.getStatus()));
			log.info("Analyzing " + inst.getName());
			// log.info("time : "+signalCandle.getDate().toString());
			log.info("previousSignal " + previousCandle);
			log.info("currentSignal " + signalCandle);

			LocalDate tradeDateTime = signalCandle.getDate().toLocalDate();
			LocalDate candleDateTime = LocalDate.now();

			// Make sure candle time and trade signal time matches
			if (tradeDateTime.isEqual(candleDateTime)) {
				if (previousCandle.getCloseAlt() > previousCandle.getOpenAlt()
						&& signalCandle.getCloseAlt() < signalCandle.getOpenAlt()) {
					log.info("Sell triggerred");
					// Sell triggerred
					if (tradeAlreadyExists) {
						Trade existingTrade = trades.stream().filter(t -> t.getToken().equals(token))
								.filter(t -> activeStatuses.contains(t.getStatus())).findFirst().orElse(null);
						existingTrade.setSellPrice(signalCandle.getPrice());
						tradeRepository.save(existingTrade);
						// Notify monitor to place the SELL order via Zerodha
						tradeMonitor.onSellTriggered(existingTrade.getId());
						System.out.println(existingTrade);
					}
				} else if (previousCandle.getCloseAlt() < previousCandle.getOpenAlt()
						&& signalCandle.getCloseAlt() > signalCandle.getOpenAlt()) {
					log.info("Buy triggerred");
					// Buy Triggerred
					if (!tradeAlreadyExists) {
						Trade trade = new Trade();
						trade.setToken(token);
						trade.setName(inst.getName());
						trade.setType(inst.getType());
						trade.setDate(signalCandle.getDate().toString());
						trade.setBuyPrice(signalCandle.getPrice());
						trade.setStatus("WATCHING");
						int tradeAmount = settingService.getInt(SettingService.GRP_ORDERS, "tradeAmount", 20000);
					trade.setNoOfShares(Math.max(1, (int) (tradeAmount / trade.getBuyPrice())));
						tradeRepository.save(trade);
						// Register into the live monitor cache so handleWatching fires on the next tick
						tradeMonitor.registerTrade(trade);
						System.out.println(trade);
					}

				}
			} else {
				log.info("Date is different. signalDate = " + tradeDateTime.toString() + " TodayDate = "
						+ candleDateTime.toString());
			}
		}
	}

	public List<TEMASeries> generateSignalSeries(List<TEMASeries> originalSeries, int altLength) {
		List<TEMASeries> newAltSeriesList = new ArrayList<>();

		int i = 0;
		int totalSize = originalSeries.size();

		while (i < totalSize) {
			int currentYear = originalSeries.get(i).getDate().getYear();
			List<TEMASeries> subset = new ArrayList<>();

			// Build the subset based on altLength and Year-End boundary
			for (int j = 0; j < altLength && (i + j) < totalSize; j++) {
				TEMASeries currentCandle = originalSeries.get(i + j);

				// Rule 4: If year changes, stop the current subset here
				if (currentCandle.getDate().getYear() != currentYear) {
					break;
				}
				subset.add(currentCandle);
			}

			// Create new AltSeries from the valid subset
			if (!subset.isEmpty()) {
				TEMASeries newAlt = new TEMASeries();
				int lastIdx = subset.size() - 1;

				// Rule 1: Date from the last candle in the subset
				newAlt.setDate(subset.get(0).getDate());

				// Rule 2: closeAlt from the last candle in the subset
				newAlt.setCloseAlt(subset.get(lastIdx).getCloseAlt());

				// Rule 3: openAlt from the FIRST candle in the subset
				newAlt.setOpenAlt(subset.get(0).getOpenAlt());
				newAlt.setPrice(subset.get(0).getPrice());
				newAltSeriesList.add(newAlt);

				// Move the pointer 'i' by the number of candles actually used
				i += subset.size();
			} else {
				i++; // Safety increment
			}
		}

		// 3. Print the new AltSeries
		// printSeries(newAltSeriesList);

		return newAltSeriesList;
	}

	public List<TEMASeries> generateAltSeries(List<CandleBar> candles, int n) {
		int size = candles.size();
		List<TEMASeries> altSeriesList = new ArrayList<>(size);

		// Extract raw series
		double[] closePrices = new double[size];
		double[] openPrices = new double[size];
		for (int i = 0; i < size; i++) {
			closePrices[i] = candles.get(i).getClose();
			openPrices[i] = candles.get(i).getOpen();
		}

		// Calculate TEMA for Close series
		double[] temaClose = calculateTEMARaw(closePrices, n);

		// Calculate TEMA for Open series
		double[] temaOpen = calculateTEMARaw(openPrices, n);

		// Map results to AltSeries objects
		for (int i = 0; i < size; i++) {
			TEMASeries alt = new TEMASeries();
			alt.setDate(candles.get(i).getTimestamp());
			alt.setCloseAlt(temaClose[i]);
			alt.setOpenAlt(temaOpen[i]);
			alt.setPrice(candles.get(i).getClose());
			altSeriesList.add(alt);
		}

		return altSeriesList;
	}

	/**
	 * Internal helper to calculate a TEMA double array.
	 */
	private double[] calculateTEMARaw(double[] prices, int n) {
		double[] ema1 = calculateEMASeries(prices, n);
		double[] ema2 = calculateEMASeries(ema1, n);
		double[] ema3 = calculateEMASeries(ema2, n);

		double[] tema = new double[prices.length];
		for (int i = 0; i < prices.length; i++) {
			if (ema1[i] == 0.0 || ema2[i] == 0.0 || ema3[i] == 0.0) {
				tema[i] = 0.0;
			} else {
				tema[i] = (3 * ema1[i]) - (3 * ema2[i]) + ema3[i];
			}
		}
		return tema;
	}

	private double[] calculateEMASeries(double[] data, int n) {
		int size = data.length;
		double[] ema = new double[size];
		if (size < n)
			return ema;

		double multiplier = 2.0 / (n + 1);

		// Find first valid data point (for nested EMAs)
		int startIdx = 0;
		while (startIdx < size && data[startIdx] == 0.0)
			startIdx++;

		if (startIdx + n > size)
			return ema;

		// Seed with SMA
		double sum = 0;
		for (int i = startIdx; i < startIdx + n; i++)
			sum += data[i];
		ema[startIdx + n - 1] = sum / n;

		// Recursive EMA
		for (int i = startIdx + n; i < size; i++) {
			ema[i] = (data[i] - ema[i - 1]) * multiplier + ema[i - 1];
		}
		return ema;
	}

}
