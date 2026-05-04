package self.sai.stock.AlgoTrading.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps the response envelope returned by AngelOne's loginByPassword API.
 * {
 *   "status": true,
 *   "message": "SUCCESS",
 *   "data": { "jwtToken": "...", "refreshToken": "...", "feedToken": "..." }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AngelLoginResponse {

    private boolean status;
    private String  message;
    private String  errorcode;
    private Data    data;

    public boolean isStatus()             { return status; }
    public void    setStatus(boolean s)   { this.status = s; }
    public String  getMessage()           { return message; }
    public void    setMessage(String m)   { this.message = m; }
    public String  getErrorcode()         { return errorcode; }
    public void    setErrorcode(String e) { this.errorcode = e; }
    public Data    getData()              { return data; }
    public void    setData(Data d)        { this.data = d; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String jwtToken;
        private String refreshToken;
        private String feedToken;

        public String getJwtToken()             { return jwtToken; }
        public void   setJwtToken(String t)     { this.jwtToken = t; }
        public String getRefreshToken()         { return refreshToken; }
        public void   setRefreshToken(String t) { this.refreshToken = t; }
        public String getFeedToken()            { return feedToken; }
        public void   setFeedToken(String t)    { this.feedToken = t; }
    }
}
