package com.example.demo;

public class TrustedCertificate
{
    final private String mKeystore;
    final private String mKeystorePassword;
    final private String mKeystoreType;
    final private String mKeystoreAlgorithm;
    final private String mTruststore;
    final private String mTruststorePassword;
    final private String mTruststoreType;
    final private String mTruststoreAlgorithm;
    public TrustedCertificate(
        String keystore,
        String keystorePassword,
        String keystoreType,
        String keystoreAlgorithm,
        String truststore,
        String truststorePassword, 
        String truststoreType, 
        String truststoreAlgorithm)
    {
        super();

        this.mKeystore = keystore;
        this.mKeystorePassword = keystorePassword;
        this.mKeystoreType = keystoreType;
        this.mKeystoreAlgorithm = keystoreAlgorithm;
        this.mTruststore = truststore;
        this.mTruststorePassword = truststorePassword;
        this.mTruststoreType = truststoreType;
        this.mTruststoreAlgorithm = truststoreAlgorithm;
    }
    public String getKeystore()
    {
        return mKeystore;
    }
    public String getKeystorePassword()
    {
        return mKeystorePassword;
    }
    public String getKeystoreType()
    {
        return mKeystoreType;
    }
    public String getKeystoreAlgorithm()
    {
        return mKeystoreAlgorithm;
    }
    public String getTruststore()
    {
        return mTruststore;
    }
    public String getTruststorePassword()
    {
        return mTruststorePassword;
    }
    public String getTruststoreType()
    {
        return mTruststoreType;
    }
    public String getTruststoreAlgorithm()
    {
        return mTruststoreAlgorithm;
    }
}
