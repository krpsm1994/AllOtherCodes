package self.sai.stock.AlgoTrading.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AngelOne getCandleData API response.
 *
 * Response shape:
 * <pre>
 * {
 *   "status": true,
 *   "message": "SUCCESS",
 *   "errorcode": "",
 *   "data": [
 *     ["2024-01-02T09:15:00+05:30", 2845.0, 2860.0, 2840.0, 2855.0, 12345],
 *     ...
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AngelCandleResponse {

    private boolean          status;
    private String           message;
    private String           errorcode;
    private List<List<Object>> data;

    public boolean isStatus()                  { return status; }
    public void    setStatus(boolean s)        { this.status = s; }

    public String  getMessage()                { return message; }
    public void    setMessage(String m)        { this.message = m; }

    public String  getErrorcode()              { return errorcode; }
    public void    setErrorcode(String e)      { this.errorcode = e; }

    public List<List<Object>> getData()        { return data; }
    public void setData(List<List<Object>> d)  { this.data = d; }

    /**
     * Converts raw array-of-arrays into typed {@link CandleBar} objects.
     * Each inner array: [timestampISO, open, high, low, close, volume]
     */
    public List<CandleBar> getCandles() {
        if (data == null) return Collections.emptyList();
        return data.stream()
                .filter(arr -> arr != null && arr.size() >= 6)
                .map(arr -> {
                    String tsStr = String.valueOf(arr.get(0));
                    double open  = toDouble(arr.get(1));
                    double high  = toDouble(arr.get(2));
                    double low   = toDouble(arr.get(3));
                    double close = toDouble(arr.get(4));
                    long   vol   = toLong(arr.get(5));

                    // Timestamp arrives as ISO offset "2024-01-02T09:15:00+05:30"
                    var dt = OffsetDateTime.parse(tsStr)
                            .atZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                            .toLocalDateTime();
                    return new CandleBar(dt, open, high, low, close, vol);
                })
                .collect(Collectors.toList());
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException e) { return 0.0; }
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (NumberFormatException e) { return 0L; }
    }
}
