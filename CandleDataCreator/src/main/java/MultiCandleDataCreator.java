import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;

public class MultiCandleDataCreator {

    public static void main(String[] args) {
        String filePath = "ticksMultiToken.json";

        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type tickListType = new TypeToken<List<TickWithToken>>() {}.getType();
            List<TickWithToken> ticks = gson.fromJson(reader, tickListType);

            System.out.println("Total ticks loaded: " + ticks.size());
            
            //All times are in IST time zone

            LocalTime start15MinTime = LocalTime.of(9, 15, 0); // market start time. which is 9:15:00
            LocalTime end15MinTime = start15MinTime.plusMinutes(15); // candle end time. Initially it is first candle end time which is 9:30:00
            LocalTime start1HourTime = LocalTime.of(9, 15, 0);
            LocalTime end1HourTime = start1HourTime.plusHours(1);
            //System.out.println("Start time : " + startTime);
            //System.out.println("End time   : " + endTime);
            
            ConcurrentHashMap<String, CandleBar> current15min = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, CandleBar> current1hour = new ConcurrentHashMap<>();
            
            ConcurrentHashMap<String, CandleBar> previous15min = new ConcurrentHashMap<>();
            ConcurrentHashMap<String, CandleBar> previous1hour = new ConcurrentHashMap<>();
            
            for (TickWithToken tick : ticks) {
            	double ltp = tick.getLastTradedPrice();
            	long currentTickVolume = tick.getVolTradedToday();
            	String token = tick.getToken();
            	LocalTime tickTime = LocalTime.parse(tick.getTime());
            	//15 Min candle calculations
            	if(tickTime.isBefore(end15MinTime)) {
            		CandleBar  current15MinCandle = current15min.get(token);
            		if(current15MinCandle == null) 
            			current15MinCandle = new CandleBar(LocalDateTime.of(LocalDate.now(), start15MinTime),0,0,0,0,0);
            		
            		if(current15MinCandle.getOpen() == 0) current15MinCandle.setOpen(ltp);
            		if(current15MinCandle.getHigh() == 0 || ltp > current15MinCandle.getHigh()) current15MinCandle.setHigh(ltp);
            		if(current15MinCandle.getLow() == 0 || ltp < current15MinCandle.getLow()) current15MinCandle.setLow(ltp);
            		
            		current15MinCandle.setClose(ltp);
            		
            		if(tickTime.isBefore(LocalTime.of(9, 30, 0))) {
            			current15MinCandle.setInitialVolume(0);
            		}
            		else {
            			CandleBar previous15MinCandle = previous15min.get(token);
            			current15MinCandle.setInitialVolume(previous15MinCandle.getPreviousCandleDayVolume());
            		}
                	
            		current15MinCandle.setVolume(currentTickVolume - current15MinCandle.getInitialVolume()); // As current tick has day volume, we subtract it with initial volume. which is 0 for first candle and last candle volume for other candles         
            		current15MinCandle.setPreviousCandleDayVolume(currentTickVolume);
            		current15min.put(token, current15MinCandle);
            	} else {
            		//previous15min = current15min; //deep copy if not copying directly
            		current15min.forEach((token1, candle) -> previous15min.put(token1, new CandleBar(candle)));
            		//TODO: In Separate Thread insert all candles to DB asynchronously. Printing it for now
            		printCandles(previous15min, "15Min");
            		current15min = new ConcurrentHashMap<>();
            		CandleBar previous15MinCandle = previous15min.get(token);
            		start15MinTime = start15MinTime.plusMinutes(15);
            		end15MinTime = end15MinTime.plusMinutes(15);
            		
            		CandleBar  current15MinCandle = new CandleBar(LocalDateTime.of(LocalDate.now(), start15MinTime),ltp,ltp,ltp,ltp,0);
            		current15MinCandle.setInitialVolume(previous15MinCandle.getPreviousCandleDayVolume());
            		current15MinCandle.setVolume(currentTickVolume - current15MinCandle.getInitialVolume());
            		current15MinCandle.setPreviousCandleDayVolume(currentTickVolume);
            		current15min.put(token, current15MinCandle);
            	}
            	
            	//1Hour candle calculation
            	if(tickTime.isBefore(end1HourTime)) {
            		CandleBar  current1HourCandle = current1hour.get(token);
            		if(current1HourCandle == null) current1HourCandle = new CandleBar();
            		
            		if(current1HourCandle.getOpen() == 0) current1HourCandle.setOpen(ltp);
            		if(current1HourCandle.getHigh() == 0 || ltp > current1HourCandle.getHigh()) current1HourCandle.setHigh(ltp);
            		if(current1HourCandle.getLow() == 0 || ltp < current1HourCandle.getLow()) current1HourCandle.setLow(ltp);
            		
            		current1HourCandle.setClose(ltp);
            		
            		if(tickTime.isBefore(LocalTime.of(10, 15, 0))) {
            			current1HourCandle.setInitialVolume(0);
            		}
            		else {
            			CandleBar previous1HourCandle = previous1hour.get(token);
            			current1HourCandle.setInitialVolume(previous1HourCandle.getPreviousCandleDayVolume());
            		}
            		current1HourCandle.setVolume(currentTickVolume - current1HourCandle.getInitialVolume()); // As current tick has day volume, we subtract it with initial volume. which is 0 for first candle and last candle volume for other candles         
            		current1HourCandle.setPreviousCandleDayVolume(currentTickVolume);
            		current1hour.put(token, current1HourCandle);
            	} else {
            		//previous1hour = current1hour; //deep copy if not copying directly
            		current1hour.forEach((token1, candle) -> previous1hour.put(token1, new CandleBar(candle)));

            		//TODO: In Separate Thread insert all candles to DB asynchronously. printing it for now
            		printCandles(previous1hour, "Hour");
            		current1hour = new ConcurrentHashMap<>();
            		CandleBar previous1HourCandle = previous1hour.get(token);
            		start1HourTime = start1HourTime.plusHours(1);
            		if(tickTime.isAfter(LocalTime.of(15, 14, 0))) {
            			end1HourTime = end1HourTime.plusMinutes(15);
            		} else {
            			end1HourTime = end1HourTime.plusHours(1);
            		}
            		
            		CandleBar  current1HourCandle = new CandleBar(LocalDateTime.of(LocalDate.now(), start1HourTime),ltp,ltp,ltp,ltp,0);
            		current1HourCandle.setInitialVolume(previous1HourCandle.getPreviousCandleDayVolume());
            		current1HourCandle.setVolume(currentTickVolume - current1HourCandle.getInitialVolume());
            		current1HourCandle.setPreviousCandleDayVolume(currentTickVolume);
            		current1hour.put(token, current1HourCandle);
            	}
            }
        } catch (IOException e) {
            System.err.println("Failed to read ticks.json: " + e.getMessage());
        }
    }
    
    public static void printCandles(ConcurrentHashMap<String, CandleBar> candlesMap, String type) {
        candlesMap.forEach((token, candle) ->
            System.out.println("type: "+type+" | Token: " + token + " | " + candle));
    }
}
