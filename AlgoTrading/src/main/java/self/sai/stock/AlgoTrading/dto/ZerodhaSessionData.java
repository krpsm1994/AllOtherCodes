package self.sai.stock.AlgoTrading.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Zerodha Kite Connect session data returned after exchanging a request_token
 * for a persistent access_token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZerodhaSessionData {

    @JsonProperty("user_id")      private String userId;
    @JsonProperty("user_name")    private String userName;
    @JsonProperty("email")        private String email;
    @JsonProperty("access_token") private String accessToken;
    @JsonProperty("public_token") private String publicToken;
    @JsonProperty("refresh_token") private String refreshToken;
    @JsonProperty("api_key")      private String apiKey;
    @JsonProperty("login_time")   private String loginTime;

    public ZerodhaSessionData() {}

    public String getUserId()              { return userId; }
    public void   setUserId(String v)      { this.userId = v; }
    public String getUserName()            { return userName; }
    public void   setUserName(String v)    { this.userName = v; }
    public String getEmail()               { return email; }
    public void   setEmail(String v)       { this.email = v; }
    public String getAccessToken()         { return accessToken; }
    public void   setAccessToken(String v) { this.accessToken = v; }
    public String getPublicToken()         { return publicToken; }
    public void   setPublicToken(String v) { this.publicToken = v; }
    public String getRefreshToken()        { return refreshToken; }
    public void   setRefreshToken(String v){ this.refreshToken = v; }
    public String getApiKey()              { return apiKey; }
    public void   setApiKey(String v)      { this.apiKey = v; }
    public String getLoginTime()           { return loginTime; }
    public void   setLoginTime(String v)   { this.loginTime = v; }
}
