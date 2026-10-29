package com.expenseanalyzer.statement.service;

import java.io.InputStream;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** S3-compatible storage (RustFS locally). Objects are private; the bucket is created on first write. Never logs keys. */
@Service
public class S3StatementStorage implements StatementStorage {

    private final S3Client s3;
    private final String bucket;
    private volatile boolean bucketReady;

    public S3StatementStorage(
            @Value("${app.storage.endpoint}") String endpoint,
            @Value("${app.storage.region:us-east-1}") String region,
            @Value("${app.storage.access-key}") String accessKey,
            @Value("${app.storage.secret-key}") String secretKey,
            @Value("${app.storage.bucket:statements}") String bucket) {
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true) // RustFS/MinIO style addressing
                .build();
        this.bucket = bucket;
    }

    @Override
    public void store(String key, InputStream content, long size, String contentType) {
        ensureBucket();
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength(size),
                RequestBody.fromInputStream(content, size));
    }

    @Override
    public InputStream read(String key) {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            throw new StatementObjectNotFoundException();
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchBucketException e) {
            // nothing stored yet
        }
    }

    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            try {
                s3.createBucket(b -> b.bucket(bucket)); // buckets are private by default; no public policy is set
            } catch (BucketAlreadyOwnedByYouException ignored) {
                // created concurrently
            }
        }
        bucketReady = true;
    }
}
