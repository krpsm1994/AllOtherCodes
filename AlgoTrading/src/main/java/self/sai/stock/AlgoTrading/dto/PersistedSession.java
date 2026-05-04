package self.sai.stock.AlgoTrading.dto;

/**
 * Wrapper persisted to the session JSON file so sessions survive restarts.
 * The 24-hour TTL is checked against {@code loginTimeEpochMs}.
 */
public class PersistedSession {

    private String            clientcode;
    private long              loginTimeEpochMs;
    private AngelLoginResponse loginResponse;
    private ZerodhaSessionData zerodhaSessionData;

    public PersistedSession() {}

    public String             getClientcode()                            { return clientcode; }
    public void               setClientcode(String c)                   { this.clientcode = c; }
    public long               getLoginTimeEpochMs()                     { return loginTimeEpochMs; }
    public void               setLoginTimeEpochMs(long t)               { this.loginTimeEpochMs = t; }
    public AngelLoginResponse getLoginResponse()                        { return loginResponse; }
    public void               setLoginResponse(AngelLoginResponse r)    { this.loginResponse = r; }
    public ZerodhaSessionData getZerodhaSessionData()                   { return zerodhaSessionData; }
    public void               setZerodhaSessionData(ZerodhaSessionData z) { this.zerodhaSessionData = z; }
}
