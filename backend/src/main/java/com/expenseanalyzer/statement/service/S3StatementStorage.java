package com.expenseanalyzer.statement.service;

import com.expenseanalyzer.common.exception.StatementObjectNotFoundException;
import com.expenseanalyzer.common.exception.StatementStorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3", matchIfMissing = true)
public class S3StatementStorage implements StatementStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final boolean serverSideEncryption;

    public S3StatementStorage(
            S3Client s3Client,
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.server-side-encryption:true}") boolean serverSideEncryption) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.serverSideEncryption = serverSideEncryption;
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        try {
            PutObjectRequest.Builder request =
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType);
            if (serverSideEncryption) {
                request.serverSideEncryption(ServerSideEncryption.AES256);
            }
            s3Client.putObject(request.build(), RequestBody.fromBytes(bytes));
        } catch (SdkException e) {
            throw new StatementStorageException("Failed to store statement object", e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
        } catch (NoSuchKeyException e) {
            throw new StatementObjectNotFoundException("Statement object not found", e);
        } catch (SdkException e) {
            throw new StatementStorageException("Failed to read statement object", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            // already gone: delete is idempotent
        } catch (SdkException e) {
            throw new StatementStorageException("Failed to delete statement object", e);
        }
    }
}
