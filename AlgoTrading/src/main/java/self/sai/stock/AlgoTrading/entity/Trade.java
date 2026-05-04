package self.sai.stock.AlgoTrading.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "trades",
       indexes = {
           @Index(name = "idx_trade_token",  columnList = "token"),
           @Index(name = "idx_trade_status", columnList = "status")
       })
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, length = 50)
    private String token;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "date", nullable = false, length = 50)
    private String date;

    @Column(name = "buyPrice", nullable = false)
    private double buyPrice;

    @Column(name = "sellPrice", nullable = false)
    private double sellPrice;

    @Column(name = "status", nullable = false, length = 255)
    private String status = "WATCHING";

    @Column(name = "noOfShares", nullable = false)
    private int noOfShares;

    @Column(name = "pnl")
    private Double pnl;

    @Column(name = "buyOrderId", length = 100)
    private String buyOrderId;

    @Column(name = "sellOrderId", length = 100)
    private String sellOrderId;

    /** Instrument watchlist type — e.g. "10MinWatchlist" or "DailyWatchlist". */
    @Column(name = "type", length = 50)
    private String type;

    public Trade() {}

    public Long   getId()                   { return id; }
    public void   setId(Long id)            { this.id = id; }
    public String getToken()                { return token; }
    public void   setToken(String t)        { this.token = t; }
    public String getName()                 { return name; }
    public void   setName(String n)         { this.name = n; }
    public String getDate()                 { return date; }
    public void   setDate(String d)         { this.date = d; }
    public double getBuyPrice()             { return buyPrice; }
    public void   setBuyPrice(double p)     { this.buyPrice = p; }
    public double getSellPrice()            { return sellPrice; }
    public void   setSellPrice(double p)    { this.sellPrice = p; }
    public String getStatus()               { return status; }
    public void   setStatus(String s)       { this.status = s; }
    public int    getNoOfShares()           { return noOfShares; }
    public void   setNoOfShares(int n)      { this.noOfShares = n; }
    public Double getPnl()                  { return pnl; }
    public void   setPnl(Double p)          { this.pnl = p; }
    public String getBuyOrderId()           { return buyOrderId; }
    public void   setBuyOrderId(String b)   { this.buyOrderId = b; }
    public String getSellOrderId()          { return sellOrderId; }
    public void   setSellOrderId(String s)  { this.sellOrderId = s; }
    public String getType()                 { return type; }
    public void   setType(String t)         { this.type = t; }
}
