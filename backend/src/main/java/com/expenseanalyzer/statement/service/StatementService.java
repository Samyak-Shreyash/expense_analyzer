package com.expenseanalyzer.statement.service;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.service.AuditService;
import com.expenseanalyzer.common.exception.InvalidFileException;
import com.expenseanalyzer.messaging.service.OutboxService;
import com.expenseanalyzer.common.exception.InvalidFileException.Reason;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Accepts statement uploads and serves owner-scoped statement metadata.
 * Logs ids and status only: never file names, hashes or contents.
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private static final Logger log = LoggerFactory.getLogger(StatementService.class);
    private static final int HEAD_BYTES = 8192;
    private static final String RESOURCE_TYPE = "STATEMENT";

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final StatementFileValidator validator;
    private final StatementStorage storage;
    private final OutboxService outboxService;
    private final AuditService auditService;

    /**
     * Validates, de-duplicates by content hash, persists metadata and stores the original file.
     * A storage failure rolls the metadata back. Queues processing via the outbox.
     */
    @Transactional
    public Statement upload(String email, MultipartFile file, String password) {
        User user = currentUser(email);
        validator.validate(file.getOriginalFilename(), file.getContentType(), file.getSize(), head(file));
        StatementFormat format = format(file.getOriginalFilename());
        String hash = sha256(file);

        Optional<Statement> existing = statementRepository.findByUserIdAndFileHash(user.getId(), hash);
        if (existing.isPresent()) {
            throw new DuplicateStatementException(existing.get().getId());
        }
        byte[] pdf = format == StatementFormat.PDF ? unlockedPdf(file, password) : null;

        Statement s = new Statement();
        s.setUserId(user.getId());
        s.setActive(true);
        s.setFileName(file.getOriginalFilename());
        s.setFileHash(hash);
        s.setFormat(format);
        s.setStatus(StatementStatus.UPLOADED);
        s.onCreate();
        s = statementRepository.saveAndFlush(s);

        String key = StatementStorage.keyFor(user.getId(), s.getId());
        s.setStorageKey(key);
        try (InputStream in = pdf != null ? new ByteArrayInputStream(pdf) : file.getInputStream()) {
            storage.store(key, in, pdf != null ? pdf.length : file.getSize(), file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // Same transaction as the row: the processing request cannot be lost (LLD 23).
        s.setStatus(StatementStatus.QUEUED);
        outboxService.enqueue(OutboxService.STATEMENT_UPLOADED, s.getId(), user.getId(), Map.of(
                "statementId", s.getId(),
                "storageKey", key,
                "format", format.name(),
                "fileHash", hash));
        auditService.record(AuditAction.STATEMENT_UPLOADED, user.getId(), RESOURCE_TYPE, s.getId());
        log.info("Statement uploaded: statementId={} format={}", s.getId(), format);
        return s;
    }

    @Transactional(readOnly = true)
    public Page<Statement> list(String email, Pageable pageable) {
        return statementRepository.findByUserIdOrderByCreatedAtDesc(currentUser(email).getId(), pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Statement> find(String email, UUID id) {
        return statementRepository.findByIdAndUserId(id, currentUser(email).getId());
    }

    /** {@link #find} for an owner viewing the statement; successful views are audited. */
    @Transactional
    public Optional<Statement> view(String email, UUID id) {
        Optional<Statement> found = find(email, id);
        found.ifPresent(s -> auditService.record(AuditAction.STATEMENT_ACCESSED, s.getUserId(), RESOURCE_TYPE, s.getId()));
        return found;
    }

    /** @return false if the statement does not exist or belongs to someone else */
    @Transactional
    public boolean delete(String email, UUID id) {
        Optional<Statement> found = find(email, id);
        if (found.isEmpty()) {
            return false;
        }
        Statement s = found.get();
        transactionRepository.deleteByStatementId(s.getId());
        statementRepository.delete(s);
        if (s.getStorageKey() != null) {
            storage.delete(s.getStorageKey());
        }
        auditService.record(AuditAction.STATEMENT_DELETED, s.getUserId(), RESOURCE_TYPE, s.getId());
        log.info("Statement deleted: statementId={}", s.getId());
        return true;
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static StatementFormat format(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return StatementFormat.CSV;
        }
        if (name.endsWith(".xls")) {
            return StatementFormat.XLS;
        }
        if (name.endsWith(".xlsx")) {
            return StatementFormat.XLSX;
        }
        if (name.endsWith(".pdf")) {
            return StatementFormat.PDF;
        }
        throw new InvalidFileException(Reason.UNSUPPORTED_EXTENSION, "This file format is not supported yet");
    }

    /**
     * Returns the PDF without encryption, so background processing never needs the password.
     * The password is used here only, in memory: never stored, queued or logged.
     */
    private static byte[] unlockedPdf(MultipartFile file, String password) {
        byte[] original;
        try {
            original = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (PDDocument doc = Loader.loadPDF(original, password == null ? "" : password, null, null,
                IOUtils.createTempFileOnlyStreamCache())) {
            if (!doc.isEncrypted()) {
                return original;
            }
            doc.setAllSecurityToBeRemoved(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (InvalidPasswordException e) {
            boolean missing = password == null || password.isEmpty();
            throw new InvalidFileException(missing ? Reason.PASSWORD_REQUIRED : Reason.WRONG_PASSWORD,
                    missing ? "This PDF is password-protected" : "The PDF password is incorrect");
        } catch (IOException e) {
            throw new InvalidFileException(Reason.SIGNATURE_MISMATCH, "File content does not match its extension");
        }
    }

    private static byte[] head(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(HEAD_BYTES);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(MultipartFile file) {
        try (DigestInputStream in = new DigestInputStream(file.getInputStream(), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(in.getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
