package self.sai.stock.AlgoTrading.dto;

/**
 * A single live tick decoded from the AngelOne SmartStream binary protocol (Quote mode = 123 bytes).
 * All prices are stored as doubles (converted from paise ÷ 100 or ÷ 100,000 for index futures).
 */
public class LiveTick {

    private String symboltoken;
    private String name;
    private long   exchangeTimestamp;   // epoch ms
    private double ltp;
    private long   lastTradedQty;
    private long   volume;
    private double open;
    private double high;
    private double low;
    private double close;               // previous day close
    private double changePercent;       // ((ltp - close) / close) * 100

    public LiveTick() {}

    public String getSymboltoken()              { return symboltoken; }
    public void   setSymboltoken(String v)      { this.symboltoken = v; }
    public String getName()                     { return name; }
    public void   setName(String v)             { this.name = v; }
    public long   getExchangeTimestamp()        { return exchangeTimestamp; }
    public void   setExchangeTimestamp(long v)  { this.exchangeTimestamp = v; }
    public double getLtp()                      { return ltp; }
    public void   setLtp(double v)              { this.ltp = v; }
    public long   getLastTradedQty()            { return lastTradedQty; }
    public void   setLastTradedQty(long v)      { this.lastTradedQty = v; }
    public long   getVolume()                   { return volume; }
    public void   setVolume(long v)             { this.volume = v; }
    public double getOpen()                     { return open; }
    public void   setOpen(double v)             { this.open = v; }
    public double getHigh()                     { return high; }
    public void   setHigh(double v)             { this.high = v; }
    public double getLow()                      { return low; }
    public void   setLow(double v)              { this.low = v; }
    public double getClose()                    { return close; }
    public void   setClose(double v)            { this.close = v; }
    public double getChangePercent()            { return changePercent; }
    public void   setChangePercent(double v)    { this.changePercent = v; }

    @Override
    public String toString() {
        return String.format("[%s] %s  LTP=%.2f  chg=%.2f%%  O=%.2f H=%.2f L=%.2f C=%.2f  vol=%d",
                symboltoken, name, ltp, changePercent, open, high, low, close, volume);
    }
}
