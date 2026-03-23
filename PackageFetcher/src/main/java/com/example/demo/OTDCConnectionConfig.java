package com.example.demo;

import java.util.List;

public class OTDCConnectionConfig {
    
    public static final String ATTR_URL = "url";
    public static final String ATTR_HANDLER = "handler";
    public static final String ATTR_HANDLER_SYSTEM_ID = "handlersystemid";
    public static final String ATTR_ACTIVE = "active";
    public static final String ATTR_ID = "id";
    public static final String ELEM_EVENTS = "events";
    
    private final String mURL;
    private final String mHandler;
    private final String mHandlerSystemId;
    private final Boolean mIsActive;
    private final String mID;
    
    //SAPDC-3648
    private final UsernameTokenConfig mTokenConfig;
    private final TrustedCertificate mCert;
    private final List<String> _events;
    
    public OTDCConnectionConfig(String id, Boolean isActive, String url, String handler, String handlerSystemId, TrustedCertificate cert, List<String> events, UsernameTokenConfig tokenConfig) {
        mURL = url;
        mHandler = handler;
        mHandlerSystemId = handlerSystemId;
        mIsActive = isActive;
        mID = id;
        mCert = cert;
        _events = events;
        //SAPDC-3648
        mTokenConfig = tokenConfig;
    }
    
    public String getURL() {
        return mURL;
    }
    
    public String getHandler() {
        return mHandler;
    }
    
	public String getHandlerSystemId() {
		return mHandlerSystemId;
	}    
    
    public Boolean isActive() {
        return  mIsActive;
    }
    
    public String getID() {
        return mID;
    }
    
    //SAPDC-3648
    public UsernameTokenConfig getTokenConfig() {
		return mTokenConfig;
	}

	public TrustedCertificate getCertificate() {
        return mCert;
    }
    
    public List<String> getEvents() {
        return _events;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Connection{");
        sb.append(ATTR_ID).append("=").append(getID()).append(";");
        sb.append(ATTR_ACTIVE).append("=").append(isActive()).append(";");
        sb.append(ATTR_URL).append("=").append(getURL()).append(";");
        sb.append(ATTR_HANDLER).append("=").append(getHandler()).append(";");
        sb.append(ATTR_HANDLER_SYSTEM_ID).append("=").append(getHandlerSystemId()).append(";");
        sb.append(ELEM_EVENTS).append("=").append(getEvents());
        sb.append("}");
        return sb.toString();
    }
}
