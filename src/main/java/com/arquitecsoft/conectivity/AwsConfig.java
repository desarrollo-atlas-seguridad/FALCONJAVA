package com.arquitecsoft.conectivity;
public class AwsConfig {

    private String accessKeyId;
    private String accessSecretKey;
    private String region;

    public AwsConfig(String accessKeyId, String accessSecretKey, String region) {
        this.accessKeyId = accessKeyId;
        this.accessSecretKey = accessSecretKey;
        this.region = region;
    }
    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessSecretKey() {
        return accessSecretKey;
    }

    public void setAccessSecretKey(String accessSecretKey) {
        this.accessKeyId = accessSecretKey;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
