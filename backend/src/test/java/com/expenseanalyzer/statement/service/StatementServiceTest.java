package com.expenseanalyzer.statement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.common.exception.DuplicateStatementException;
import com.expenseanalyzer.common.exception.InvalidFileException;
import com.expenseanalyzer.common.exception.StatementNotFoundException;
import com.expenseanalyzer.common.exception.StatementStorageException;
import com.expenseanalyzer.statement.dto.StatementUploadResponse;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    private static final String EMAIL = "alice@example.com";
    private static final byte[] CSV = "date,amount\n2026-01-01,10\n".getBytes(StandardCharsets.UTF_8);
    // SHA-256 of CSV
    private static final String CSV_SHA256 = sha(CSV);

    @Mock private StatementRepository statementRepository;
    @Mock private UserRepository userRepository;
    @Mock private StatementStorage storage;

    private StatementService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new StatementService(statementRepository, userRepository, new StatementFileValidator(20971520), storage);
        userId = UUID.randomUUID();
        User alice = new User();
        alice.setId(userId);
        alice.setEmail(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(alice));
    }

    @Test
    void uploadSavesQueuedRowThenStoresBytesUnderOwnerScopedKey() {
        when(statementRepository.findByUserIdAndSha256(userId, CSV_SHA256)).thenReturn(Optional.empty());
        when(statementRepository.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));

        StatementUploadResponse res = service.upload(EMAIL, "statement.csv", "text/csv", CSV);

        ArgumentCaptor<Statement> saved = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).save(saved.capture());
        Statement s = saved.getValue();
        assertThat(s.getUserId()).isEqualTo(userId);
        assertThat(s.getStatus()).isEqualTo(StatementStatus.QUEUED);
        assertThat(s.getSha256()).isEqualTo(CSV_SHA256);
        assertThat(s.getSizeBytes()).isEqualTo(CSV.length);
        assertThat(s.getObjectKey()).isEqualTo("statements/" + userId + "/" + s.getId());
        verify(storage).put(s.getObjectKey(), CSV, "text/csv");
        assertThat(res.id()).isEqualTo(s.getId());
        assertThat(res.status()).isEqualTo(StatementStatus.QUEUED);
        assertThat(res.fileName()).isEqualTo("statement.csv");
    }

    @Test
    void uploadDeletesRowWhenStorageFails() {
        when(statementRepository.findByUserIdAndSha256(userId, CSV_SHA256)).thenReturn(Optional.empty());
        when(statementRepository.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new StatementStorageException("down", null)).when(storage).put(anyString(), any(), anyString());

        assertThatThrownBy(() -> service.upload(EMAIL, "statement.csv", "text/csv", CSV))
                .isInstanceOf(StatementStorageException.class);

        ArgumentCaptor<Statement> saved = ArgumentCaptor.forClass(Statement.class);
        verify(statementRepository).save(saved.capture());
        verify(statementRepository).delete(saved.getValue());
    }

    @Test
    void duplicateContentIsRejectedWithExistingIdAndNothingStored() {
        Statement existing = new Statement(userId, null, null);
        when(statementRepository.findByUserIdAndSha256(userId, CSV_SHA256)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.upload(EMAIL, "again.csv", "text/csv", CSV))
                .isInstanceOfSatisfying(DuplicateStatementException.class,
                        e -> assertThat(e.getExistingId()).isEqualTo(existing.getId()));
        verify(statementRepository, never()).save(any());
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void invalidFileIsRejectedBeforeAnyWrite() {
        assertThatThrownBy(() -> service.upload(EMAIL, "statement.exe", "text/csv", CSV))
                .isInstanceOf(InvalidFileException.class);
        verify(statementRepository, never()).save(any());
        verify(storage, never()).put(anyString(), any(), anyString());
    }

    @Test
    void deleteRemovesObjectThenRow() {
        Statement s = new Statement(userId, null, null);
        s.setObjectKey("statements/" + userId + "/" + s.getId());
        when(statementRepository.findByIdAndUserId(s.getId(), userId)).thenReturn(Optional.of(s));

        service.delete(EMAIL, s.getId());

        var order = org.mockito.Mockito.inOrder(storage, statementRepository);
        order.verify(storage).delete(s.getObjectKey());
        order.verify(statementRepository).delete(s);
    }

    @Test
    void deleteOfStatementNotOwnedIsNotFoundAndTouchesNothing() {
        UUID id = UUID.randomUUID();
        when(statementRepository.findByIdAndUserId(eq(id), eq(userId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(EMAIL, id)).isInstanceOf(StatementNotFoundException.class);
        verify(storage, never()).delete(anyString());
        verify(statementRepository, never()).delete(any());
    }

    @Test
    void objectKeyNeverContainsOriginalFilename() {
        when(statementRepository.findByUserIdAndSha256(userId, CSV_SHA256)).thenReturn(Optional.empty());
        when(statementRepository.save(any(Statement.class))).thenAnswer(inv -> inv.getArgument(0));

        service.upload(EMAIL, "my-secret-bank-march.csv", "text/csv", CSV);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), any(), anyString());
        assertThat(key.getValue()).startsWith("statements/" + userId + "/")
                .doesNotContain("secret").doesNotContain("march").doesNotContain(".csv");
    }

    @Test
    void getAndStatusOfStatementNotOwnedAreNotFoundAndNeverTouchStorage() {
        UUID id = UUID.randomUUID();
        when(statementRepository.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(EMAIL, id)).isInstanceOf(StatementNotFoundException.class);
        assertThatThrownBy(() -> service.status(EMAIL, id)).isInstanceOf(StatementNotFoundException.class);
        assertThatThrownBy(() -> service.delete(EMAIL, id)).isInstanceOf(StatementNotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void deleteKeepsRowWhenStorageIsDown() {
        Statement s = new Statement(userId, null, null);
        s.setObjectKey("statements/" + userId + "/" + s.getId());
        when(statementRepository.findByIdAndUserId(s.getId(), userId)).thenReturn(Optional.of(s));
        doThrow(new StatementStorageException("down", null)).when(storage).delete(s.getObjectKey());

        assertThatThrownBy(() -> service.delete(EMAIL, s.getId())).isInstanceOf(StatementStorageException.class);
        verify(statementRepository, never()).delete(any());
    }

    private static String sha(byte[] b) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
