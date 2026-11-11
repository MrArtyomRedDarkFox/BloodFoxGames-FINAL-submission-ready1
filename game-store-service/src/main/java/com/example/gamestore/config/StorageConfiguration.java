package com.example.gamestore.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.net.URI;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class StorageConfiguration {
    @Bean
    S3Client s3Client(StorageProperties p) {
        return S3Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).endpointOverride(URI.create(p.getEndpoint()))
            .region(Region.of(p.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }
    @Bean
    S3Presigner s3Presigner(StorageProperties p) {
        return S3Presigner.builder().endpointOverride(URI.create(p.getPublicEndpoint()))
            .region(Region.of(p.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey())))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build()).build();
    }
}
