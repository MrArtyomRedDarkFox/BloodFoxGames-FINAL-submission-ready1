package com.example.gamestore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {
    private String bucket, region, endpoint, publicEndpoint, accessKey, secretKey, adminToken;
    public String getBucket(){return bucket;} public void setBucket(String v){bucket=v;}
    public String getRegion(){return region;} public void setRegion(String v){region=v;}
    public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
    public String getPublicEndpoint(){return publicEndpoint;} public void setPublicEndpoint(String v){publicEndpoint=v;}
    public String getAccessKey(){return accessKey;} public void setAccessKey(String v){accessKey=v;}
    public String getSecretKey(){return secretKey;} public void setSecretKey(String v){secretKey=v;}
    public String getAdminToken(){return adminToken;} public void setAdminToken(String v){adminToken=v;}
}
