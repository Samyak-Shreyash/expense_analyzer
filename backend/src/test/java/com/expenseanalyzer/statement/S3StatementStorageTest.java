package com.expenseanalyzer.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.common.exception.StatementObjectNotFoundException;
import com.expenseanalyzer.common.exception.StatementStorageException;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3StatementStorageTest {

    private static final String KEY = "statements/1/2";

    @Mock
    S3Client s3Client;

    private S3StatementStorage storage() {
        return new S3StatementStorage(s3Client, "bucket");
    }

    @Test
    void putSendsBucketKeyAndContentType() {
        storage().put(KEY, new byte[] {1, 2}, "text/csv");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo("bucket");
        assertThat(captor.getValue().key()).isEqualTo(KEY);
        assertThat(captor.getValue().contentType()).isEqualTo("text/csv");
    }

    @Test
    void putWrapsSdkFailure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> storage().put(KEY, new byte[] {1}, "text/csv"))
                .isInstanceOf(StatementStorageException.class);
    }

    @Test
    void getReturnsBytes() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {9, 8}));

        assertThat(storage().get(KEY)).containsExactly(9, 8);
    }

    @Test
    void getMissingKeyThrowsNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> storage().get(KEY)).isInstanceOf(StatementObjectNotFoundException.class);
    }

    @Test
    void getWrapsSdkFailure() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> storage().get(KEY)).isInstanceOf(StatementStorageException.class);
    }

    @Test
    void deleteSendsBucketAndKey() {
        storage().delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("bucket");
        assertThat(captor.getValue().key()).isEqualTo(KEY);
    }

    @Test
    void deleteOfMissingKeyIsIdempotent() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertThatCode(() -> storage().delete(KEY)).doesNotThrowAnyException();
    }

    @Test
    void deleteWrapsSdkFailure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(SdkClientException.create("boom"));

        assertThatThrownBy(() -> storage().delete(KEY)).isInstanceOf(StatementStorageException.class);
    }
}
