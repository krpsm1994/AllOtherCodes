package self.sai.stock.AlgoTrading.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps one entry from the AngelOne OpenAPIScripMaster JSON.
 * All fields arrive as Strings from the API.
 *
 * Example record:
 * {
 *   "token": "2885",
 *   "symbol": "RELIANCE-EQ",
 *   "name": "RELIANCE",
 *   "expiry": "",
 *   "strike": "-1.000000",
 *   "lotsize": "1",
 *   "instrumenttype": "",
 *   "exch_seg": "NSE",
 *   "tick_size": "5.000000"
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScripMasterEntry {

    @JsonProperty("token")
    private String token;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("name")
    private String name;

    @JsonProperty("expiry")
    private String expiry;

    @JsonProperty("strike")
    private String strike;

    @JsonProperty("lotsize")
    private String lotsize;

    @JsonProperty("instrumenttype")
    private String instrumenttype;

    @JsonProperty("exch_seg")
    private String exchSeg;

    @JsonProperty("tick_size")
    private String tickSize;

    public String getToken()          { return token; }
    public void   setToken(String v)  { this.token = v; }

    public String getSymbol()         { return symbol; }
    public void   setSymbol(String v) { this.symbol = v; }

    public String getName()           { return name; }
    public void   setName(String v)   { this.name = v; }

    public String getExpiry()         { return expiry; }
    public void   setExpiry(String v) { this.expiry = v; }

    public String getStrike()         { return strike; }
    public void   setStrike(String v) { this.strike = v; }

    public String getLotsize()        { return lotsize; }
    public void   setLotsize(String v){ this.lotsize = v; }

    public String getInstrumenttype()         { return instrumenttype; }
    public void   setInstrumenttype(String v) { this.instrumenttype = v; }

    public String getExchSeg()        { return exchSeg; }
    public void   setExchSeg(String v){ this.exchSeg = v; }

    public String getTickSize()        { return tickSize; }
    public void   setTickSize(String v){ this.tickSize = v; }
}
