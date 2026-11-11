package com.example.gamestore.storage;

import com.example.gamestore.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.time.Duration;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class S3StorageService {
    private final S3Client client; private final S3Presigner presigner; private final StorageProperties properties;
    public S3StorageService(S3Client client, S3Presigner presigner, StorageProperties properties) { this.client=client; this.presigner=presigner; this.properties=properties; }
    @PostConstruct public void ensureBucket() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
                return;
            } catch (Exception missing) {
                try {
                    client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
                    return;
                } catch (Exception unavailable) {
                    last = new IllegalStateException("S3 storage is not ready (attempt " + attempt + ")", unavailable);
                    try { Thread.sleep(2000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted while waiting for S3", interrupted); }
                }
            }
        }
        throw last == null ? new IllegalStateException("S3 storage initialization failed") : last;
    }
    public String upload(byte[] bytes, String contentType, String extension) {
        String key = "uploads/" + UUID.randomUUID() + extension;
        uploadAtKey(key, bytes, contentType);
        return key;
    }

    public String uploadAvatar(long userId, byte[] bytes, String contentType, String extension) {
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + extension;
        uploadAtKey(key, bytes, contentType);
        return key;
    }

    private void uploadAtKey(String key, byte[] bytes, String contentType) {
        client.putObject(PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(contentType)
                .cacheControl("private, max-age=0, no-cache")
                .build(), RequestBody.fromBytes(bytes));
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        client.deleteObject(builder -> builder.bucket(properties.getBucket()).key(key));
    }
    public String downloadUrl(String key) {
        var request = GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build();
        var presign = GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(15)).getObjectRequest(request).build();
        return presigner.presignGetObject(presign).url().toString();
    }
    public String bucket(){ return properties.getBucket(); }
    public boolean isReady(){ client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build()); return true; }
}
