import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalTime;
import java.util.List;

public class CandleDataCreator {

    public static void main(String[] args) {
        String filePath = "ticks.json";

        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type tickListType = new TypeToken<List<Tick>>() {}.getType();
            List<Tick> ticks = gson.fromJson(reader, tickListType);

            System.out.println("Total ticks loaded: " + ticks.size());
            
            //All times are in IST time zone

            LocalTime startTime = LocalTime.of(9, 15, 0); // market start time. which is 9:15:00
            LocalTime endTime = startTime.plusMinutes(15); // candle end time. Initially it is first candle end time which is 9:30:00
            System.out.println("Start time : " + startTime);
            System.out.println("End time   : " + endTime);
            
            //For every instrument these are the default values when subscribed for web socket connection. assuming initial values just before 9:15:00 candle starts
            double open =0, high =0, low=0, close=0;
            long volume =0;
            long initialVolume = 0;
            long lastCandleLastTickVolume = 0;

            for (Tick tick : ticks) {// assume each iteration is different tick received for that instrument in real time through web socket
            	double ltp = tick.getLastTradedPrice(); // ltp received through tick after conversion from binary data
            	long currentTickVolume = tick.getVolTradedToday(); // volume received through tick after conversion from binary data
                LocalTime tickTime = LocalTime.parse(tick.getTime()); // this should be current tick time(now). mocked in the tick for testing
                if (tickTime.isBefore(endTime)) { //If tick time is before candle end time calculate below
                	//These first three if statements will execute for first tick of the day at 9:15:00. we initialized open high low to 0 for first candle.
                	if(open == 0) open = ltp;
                	if(high == 0) high = open;
                	if(low == 0) low = open;
                	// these are actual calculations to determine high, low, close and volume of current candle
                	if(high < ltp) high = ltp;
                	if(low > ltp) low = ltp;
                	close = ltp;
                	volume = currentTickVolume - initialVolume; // As current tick has day volume, we subtract it with initial volume. which is 0 for first candle and last candle volume for other candles         
                	lastCandleLastTickVolume = currentTickVolume;
                } else { //If tick time is After candle end time update for current tick. This will be the first tick of next candle
                	//Instead of printing to console. collect the candle data and post it to DB with 15Min type and instrument details
                	System.out.println("candle Time : "+ startTime + ", Open = "+open+", High = "+high+", Low = "+low+", Close = "+close+", Volume = "+volume);
                	//Update the candle timings and initial volume for next candle
                	initialVolume = lastCandleLastTickVolume; // Update next candle initial volume to last candle lat tick volume
                	startTime = endTime; // update start time to next candle start time ex: moving startTime to 9:30:00 from 9:15:00
                	endTime = endTime.plusMinutes(15); // update end time to next candle end time ex: moving endTime to 9:45:00 from 9:30:00
                	//below are the initial data for next candle.
                	open = ltp;
                	high = open;
                	low= open;
                	close = ltp;
                	volume = currentTickVolume - initialVolume;
                }
            }

        } catch (IOException e) {
            System.err.println("Failed to read ticks.json: " + e.getMessage());
        }
    }
}
