package self.sai.stock.AlgoTrading.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @Column(name = "token", nullable = false, length = 50)
    private String token;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "exchange", nullable = false, length = 50)
    private String exchange;

    @Column(name = "lotSize", nullable = false)
    private int lotSize = 1;

    @Column(name = "type", nullable = false, length = 50)
    private String type = "FUTURE";

    public Instrument() {}

    public String getToken()            { return token; }
    public void   setToken(String t)    { this.token = t; }
    public String getName()             { return name; }
    public void   setName(String n)     { this.name = n; }
    public String getExchange()         { return exchange; }
    public void   setExchange(String e) { this.exchange = e; }
    public int    getLotSize()          { return lotSize; }
    public void   setLotSize(int l)     { this.lotSize = l; }
    public String getType()             { return type; }
    public void   setType(String t)     { this.type = t; }
}
