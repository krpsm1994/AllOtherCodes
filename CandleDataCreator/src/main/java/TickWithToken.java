import com.google.gson.annotations.SerializedName;

public class TickWithToken {

    @SerializedName("last_traded_price")
    private double lastTradedPrice;

    @SerializedName("vol_traded_today")
    private long volTradedToday;

    @SerializedName("time")
    private String time;
    
    @SerializedName("token")
    private String token;

    public String getToken() {
		return token;
	}

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
        return "Tick{token="+token+", "+"time=" + time + ", lastTradedPrice=" + lastTradedPrice + ", volTradedToday=" + volTradedToday + "}";
    }
}
