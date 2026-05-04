package self.sai.stock.AlgoTrading.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "candles",
       indexes = {
           @Index(name = "idx_candle_token", columnList = "token"),
           @Index(name = "idx_candle_date",  columnList = "date")
       })
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, length = 50)
    private String token;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "date", nullable = false, length = 50)
    private String date;

    @Column(name = "open", nullable = false)
    private double open = 0;

    @Column(name = "high", nullable = false)
    private double high = 0;

    @Column(name = "low", nullable = false)
    private double low = 0;

    @Column(name = "close", nullable = false)
    private double close = 0;

    @Column(name = "closeAlt", nullable = false)
    private double closeAlt = 0;

    @Column(name = "openAlt", nullable = false)
    private double openAlt = 0;

    @Column(name = "type", nullable = false, length = 10)
    private String type = "10Min";

    public Candle() {}

    public Long   getId()               { return id; }
    public void   setId(Long id)        { this.id = id; }
    public String getToken()            { return token; }
    public void   setToken(String t)    { this.token = t; }
    public String getName()             { return name; }
    public void   setName(String n)     { this.name = n; }
    public String getDate()             { return date; }
    public void   setDate(String d)     { this.date = d; }
    public double getOpen()             { return open; }
    public void   setOpen(double o)     { this.open = o; }
    public double getHigh()             { return high; }
    public void   setHigh(double h)     { this.high = h; }
    public double getLow()              { return low; }
    public void   setLow(double l)      { this.low = l; }
    public double getClose()            { return close; }
    public void   setClose(double c)    { this.close = c; }
    public double getCloseAlt()         { return closeAlt; }
    public void   setCloseAlt(double c) { this.closeAlt = c; }
    public double getOpenAlt()          { return openAlt; }
    public void   setOpenAlt(double o)  { this.openAlt = o; }
    public String getType()             { return type; }
    public void   setType(String t)     { this.type = t; }
}
