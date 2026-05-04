package self.sai.stock.AlgoTrading.dto;

import java.time.LocalDateTime;

/** One OHLCV candle bar returned from AngelOne's getCandleData API. */
public class CandleBar {

    private LocalDateTime timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long   volume;

    public CandleBar() {}

    public CandleBar(LocalDateTime timestamp, double open, double high,
                     double low, double close, long volume) {
        this.timestamp = timestamp;
        this.open      = open;
        this.high      = high;
        this.low       = low;
        this.close     = close;
        this.volume    = volume;
    }

    public LocalDateTime getTimestamp()         { return timestamp; }
    public void setTimestamp(LocalDateTime t)   { this.timestamp = t; }
    public double getOpen()                     { return open; }
    public void setOpen(double o)               { this.open = o; }
    public double getHigh()                     { return high; }
    public void setHigh(double h)               { this.high = h; }
    public double getLow()                      { return low; }
    public void setLow(double l)                { this.low = l; }
    public double getClose()                    { return close; }
    public void setClose(double c)              { this.close = c; }
    public long getVolume()                     { return volume; }
    public void setVolume(long v)               { this.volume = v; }
}
