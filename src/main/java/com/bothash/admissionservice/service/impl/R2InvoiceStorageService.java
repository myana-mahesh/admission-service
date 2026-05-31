package com.bothash.admissionservice.service.impl;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Service
public class R2InvoiceStorageService {

    @Value("${storage.r2.enabled:false}")
    private boolean enabled;

    @Value("${storage.r2.account-id:}")
    private String accountId;

    @Value("${storage.r2.bucket:}")
    private String bucket;

    @Value("${storage.r2.access-key-id:}")
    private String accessKeyId;

    @Value("${storage.r2.secret-access-key:}")
    private String secretAccessKey;

    @Value("${storage.r2.presign-minutes:15}")
    private long presignMinutes;

    private S3Client s3Client;
    private S3Presigner s3Presigner;

    @PostConstruct
    void init() {
        if (!enabled || !hasCredentials()) {
            enabled = false;
            return;
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim());
        URI endpoint = URI.create("https://" + accountId.trim() + ".r2.cloudflarestorage.com");

        this.s3Client = S3Client.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void upload(String key, byte[] bytes) {
        upload(key, bytes, "application/pdf");
    }

    public void upload(String key, byte[] bytes, String contentType) {
        if (!enabled) {
            throw new IllegalStateException("R2 storage is not configured.");
        }
        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (StringUtils.hasText(contentType)) {
            request.contentType(contentType);
        }
        s3Client.putObject(request.build(), RequestBody.fromBytes(bytes));
    }

    public String marker(String key) {
        return "r2://" + key;
    }

    public String encodeKey(String key) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }

    public String decodeKey(String token) {
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    public URI presignGet(String key) {
        return presignGet(key, null);
    }

    public URI presignGet(String key, String downloadFileName) {
        if (!enabled || !StringUtils.hasText(key)) {
            throw new IllegalStateException("R2 storage is not configured.");
        }
        GetObjectRequest.Builder requestBuilder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (StringUtils.hasText(downloadFileName)) {
            requestBuilder.responseContentDisposition("attachment; filename=\"" + downloadFileName.replace("\"", "") + "\"");
        }
        GetObjectRequest getObjectRequest = requestBuilder.build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(Math.max(1, presignMinutes)))
                .getObjectRequest(getObjectRequest)
                .build();
        return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
    }

    public void delete(String key) {
        if (!enabled || !StringUtils.hasText(key)) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    public boolean exists(String key) {
        if (!enabled || !StringUtils.hasText(key)) {
            return false;
        }
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("Not Found")) {
                return false;
            }
            throw ex;
        }
    }

    public byte[] download(String key) {
        if (!enabled || !StringUtils.hasText(key)) {
            return null;
        }
        ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        return response.asByteArray();
    }

    public void deleteByMarkerOrUrl(String value) {
        if (!enabled || !StringUtils.hasText(value)) {
            return;
        }
        String key = extractKey(value);
        if (StringUtils.hasText(key)) {
            delete(key);
        }
    }

    public String extractKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("r2://")) {
            return trimmed.substring("r2://".length());
        }
        String objectPrefix = "/storage/object/";
        int objectIndex = trimmed.indexOf(objectPrefix);
        if (objectIndex >= 0) {
            return decodeKey(trimmed.substring(objectIndex + objectPrefix.length()));
        }
        String invoicePrefix = "/api/invoices/r2/";
        int invoiceIndex = trimmed.indexOf(invoicePrefix);
        if (invoiceIndex >= 0) {
            return decodeKey(trimmed.substring(invoiceIndex + invoicePrefix.length()));
        }
        return null;
    }

    private boolean hasCredentials() {
        return StringUtils.hasText(accountId)
                && StringUtils.hasText(bucket)
                && StringUtils.hasText(accessKeyId)
                && StringUtils.hasText(secretAccessKey);
    }
}
