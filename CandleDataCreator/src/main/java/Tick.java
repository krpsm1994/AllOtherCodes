import com.google.gson.annotations.SerializedName;

public class Tick {

    @SerializedName("last_traded_price")
    private double lastTradedPrice;

    @SerializedName("vol_traded_today")
    private long volTradedToday;

    @SerializedName("time")
    private String time;

    public double getLastTradedPrice() {
        return lastTradedPrice;
    }

    public long getVolTradedToday() {
        return volTradedToday;
    }

    public String getTime() {
        return time;
    }

    @Override
    public String toString() {
        return "Tick{time=" + time + ", lastTradedPrice=" + lastTradedPrice + ", volTradedToday=" + volTradedToday + "}";
    }
}
