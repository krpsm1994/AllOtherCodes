import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class CandleBar {

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private long initialVolume;
    private long previousCandleDayVolume;

    public CandleBar() {}
    
    public CandleBar(CandleBar candle) {
        this.timestamp               = candle.timestamp;
        this.open                    = candle.open;
        this.high                    = candle.high;
        this.low                     = candle.low;
        this.close                   = candle.close;
        this.volume                  = candle.volume;
        this.initialVolume           = candle.initialVolume;
        this.previousCandleDayVolume = candle.previousCandleDayVolume;
    }

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

	public long getInitialVolume() {
		return initialVolume;
	}

	public void setInitialVolume(long initialVolume) {
		this.initialVolume = initialVolume;
	}

	public long getPreviousCandleDayVolume() {
		return previousCandleDayVolume;
	}

	public void setPreviousCandleDayVolume(long previousCandleDayVolume) {
		this.previousCandleDayVolume = previousCandleDayVolume;
	}

	@Override
	public String toString() {
		return "CandleBar{timestamp=" + timestamp +
				", open=" + open +
				", high=" + high +
				", low=" + low +
				", close=" + close +
				", volume=" + volume +
				", initialVolume=" + initialVolume +
				", previousCandleDayVolume=" + previousCandleDayVolume +
				"}";
	}
    
    
}
