package self.sai.stock.AlgoTrading.dto;

/** Unified candle row returned by both the DB-fetch and the live daily-fetch endpoints. */
public class CandleRowDto {

    private String date;
    private double open;
    private double high;
    private double low;
    private double close;
    private long   volume;

    public CandleRowDto() {}

    public CandleRowDto(String date, double open, double high,
                        double low, double close, long volume) {
        this.date   = date;
        this.open   = open;
        this.high   = high;
        this.low    = low;
        this.close  = close;
        this.volume = volume;
    }

    public String getDate()           { return date; }
    public void   setDate(String d)   { this.date = d; }
    public double getOpen()           { return open; }
    public void   setOpen(double o)   { this.open = o; }
    public double getHigh()           { return high; }
    public void   setHigh(double h)   { this.high = h; }
    public double getLow()            { return low; }
    public void   setLow(double l)    { this.low = l; }
    public double getClose()          { return close; }
    public void   setClose(double c)  { this.close = c; }
    public long   getVolume()         { return volume; }
    public void   setVolume(long v)   { this.volume = v; }
}
