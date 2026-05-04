package self.sai.stock.AlgoTrading.dto;

/** Credentials supplied by the user to log in via AngelOne. */
public class LoginRequest {
    private String clientcode;
    private String pin;
    private String totp;

    public LoginRequest() {}
    public LoginRequest(String clientcode, String pin, String totp) {
        this.clientcode = clientcode;
        this.pin        = pin;
        this.totp       = totp;
    }

    public String getClientcode()            { return clientcode; }
    public void   setClientcode(String c)    { this.clientcode = c; }
    public String getPin()                   { return pin; }
    public void   setPin(String p)           { this.pin = p; }
    public String getTotp()                  { return totp; }
    public void   setTotp(String t)          { this.totp = t; }
}
