package com.expenseanalyzer.statement.service;

import com.expenseanalyzer.common.exception.DuplicateStatementException;
import com.expenseanalyzer.common.exception.StatementNotFoundException;
import com.expenseanalyzer.common.exception.StatementStorageException;
import com.expenseanalyzer.statement.dto.StatementResponse;
import com.expenseanalyzer.statement.dto.StatementStatusResponse;
import com.expenseanalyzer.statement.dto.StatementUploadResponse;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.user.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Upload, read and delete of the caller's statements. Every operation is scoped to the owner
 * resolved from the token subject. Never logs filenames or file contents.
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);
    private static final int HEAD_BYTES = 8192;
    private static final String DEFAULT_CURRENCY = "USD";

    private final StatementRepository statementRepository;
    private final UserRepository userRepository;
    private final StatementFileValidator validator;
    private final StatementStorage storage;

    public StatementUploadResponse upload(String email, String filename, String contentType, byte[] bytes) {
        UUID userId = resolveUserId(email);
        validator.validate(filename, contentType, bytes.length, Arrays.copyOf(bytes, Math.min(bytes.length, HEAD_BYTES)));
        String sha256 = sha256Hex(bytes);
        statementRepository.findByUserIdAndSha256(userId, sha256).ifPresent(existing -> {
            throw new DuplicateStatementException(existing.getId());
        });

        Statement statement = new Statement(userId, null, null);
        statement.setOriginalFilename(filename);
        statement.setContentType(contentType);
        statement.setSizeBytes((long) bytes.length);
        statement.setSha256(sha256);
        statement.setObjectKey("statements/" + userId + "/" + statement.getId());
        statement.setStatus(StatementStatus.QUEUED);
        statement.setCurrencyCode(DEFAULT_CURRENCY);
        statement.onCreate();
        try {
            statement = statementRepository.save(statement);
        } catch (DataIntegrityViolationException e) {
            // A concurrent upload of the same content won the unique (user_id, sha256) constraint.
            Statement existing = statementRepository.findByUserIdAndSha256(userId, sha256).orElseThrow(() -> e);
            throw new DuplicateStatementException(existing.getId());
        }

        try {
            storage.put(statement.getObjectKey(), bytes, contentType);
        } catch (StatementStorageException e) {
            statementRepository.delete(statement);
            log.warn("Statement upload rolled back after storage failure: statementId={}", statement.getId());
            throw e;
        }
        log.info("Statement queued: statementId={}", statement.getId());
        return new StatementUploadResponse(statement.getId(), statement.getStatus(), statement.getOriginalFilename());
    }

    public List<StatementResponse> list(String email) {
        return statementRepository.findByUserIdOrderByCreatedAtDesc(resolveUserId(email)).stream()
                .map(StatementResponse::from)
                .toList();
    }

    public StatementResponse get(String email, UUID id) {
        return StatementResponse.from(owned(email, id));
    }

    public StatementStatusResponse status(String email, UUID id) {
        Statement s = owned(email, id);
        return new StatementStatusResponse(s.getId(), s.getStatus(), s.getErrorCode());
    }

    public void delete(String email, UUID id) {
        Statement statement = owned(email, id);
        storage.delete(statement.getObjectKey());
        statementRepository.delete(statement);
        log.info("Statement deleted: statementId={}", id);
    }

    private Statement owned(String email, UUID id) {
        return statementRepository.findByIdAndUserId(id, resolveUserId(email))
                .orElseThrow(StatementNotFoundException::new);
    }

    private UUID resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED))
                .getId();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
