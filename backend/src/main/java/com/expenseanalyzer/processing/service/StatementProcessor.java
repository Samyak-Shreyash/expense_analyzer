package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.merchant.service.MerchantMatch;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.merchant.service.MerchantService;
import com.expenseanalyzer.messaging.service.OutboxService;
import com.expenseanalyzer.processing.model.ProcessedEvent;
import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import com.expenseanalyzer.processing.parser.csv.CsvStatementParser;
import com.expenseanalyzer.processing.parser.excel.ExcelStatementParser;
import com.expenseanalyzer.processing.repository.ProcessedEventRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementObjectNotFoundException;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes StatementUploaded: parses the stored file and persists unseen transactions.
 * <p>
 * Idempotency (LLD 22.1, 24): the event id is recorded in {@code processed_events} in the same transaction
 * as the transactions, a COMPLETED statement is never re-imported, and transaction fingerprints are unique
 * per user, so duplicate or concurrent delivery converges to one dataset. Transient failures (storage down)
 * propagate so the event is redelivered; deterministic failures mark the statement FAILED and acknowledge.
 * Logs ids and codes only.
 */
@Service
public class StatementProcessor {

    static final String CONSUMER = "statement-processor";
    private static final int SUPPORTED_VERSION = 1;
    // amount_cents is numeric(12,2): at most 10 integer digits
    private static final int MAX_CENT_DIGITS = 10;
    private static final Logger log = LoggerFactory.getLogger(StatementProcessor.class);

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final StatementStorage storage;
    private final CsvStatementParser csvParser;
    private final ExcelStatementParser excelParser;
    private final JsonMapper jsonMapper;
    private final MerchantNormalizer merchantNormalizer;
    private final MerchantService merchantService;
    private final ParserLimits limits;

    public StatementProcessor(StatementRepository statementRepository, TransactionRepository transactionRepository,
                              ProcessedEventRepository processedEventRepository, StatementStorage storage,
                              CsvStatementParser csvParser, ExcelStatementParser excelParser, JsonMapper jsonMapper,
                              MerchantNormalizer merchantNormalizer, MerchantService merchantService,
                              @Value("${app.parser.max-rows:50000}") int maxRows,
                              @Value("${app.parser.max-columns:50}") int maxColumns,
                              @Value("${app.parser.timeout:30s}") Duration timeout) {
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.processedEventRepository = processedEventRepository;
        this.storage = storage;
        this.csvParser = csvParser;
        this.excelParser = excelParser;
        this.jsonMapper = jsonMapper;
        this.merchantNormalizer = merchantNormalizer;
        this.merchantService = merchantService;
        this.limits = new ParserLimits(maxRows, maxColumns, timeout);
    }

    private record Envelope(UUID eventId, UUID userId, UUID statementId) {
    }

    @Transactional
    public void process(String message) {
        Optional<Envelope> parsed = envelope(message);
        if (parsed.isEmpty()) {
            return;
        }
        Envelope e = parsed.get();
        if (processedEventRepository.existsById(new ProcessedEvent.Key(e.eventId(), CONSUMER))) {
            log.info("Duplicate event skipped: eventId={}", e.eventId());
            return;
        }

        Optional<Statement> found = statementRepository.findById(e.statementId());
        if (found.isEmpty()) {
            log.info("Event for deleted statement acknowledged: eventId={}", e.eventId());
        } else if (!found.get().getUserId().equals(e.userId())) {
            log.warn("Event user does not own statement; ignored: eventId={} statementId={}", e.eventId(), e.statementId());
            return; // not marked processed: nothing legitimate happened
        } else if (found.get().getStatus() == StatementStatus.COMPLETED) {
            log.info("Statement already completed; replay acknowledged: statementId={}", e.statementId());
        } else {
            importStatement(found.get());
        }
        processedEventRepository.save(new ProcessedEvent(e.eventId(), CONSUMER));
    }

    private void importStatement(Statement s) {
        ParsedStatement parsed;
        try (InputStream in = storage.read(s.getStorageKey())) {
            parsed = switch (s.getFormat()) {
                case CSV -> csvParser.parse(in, limits);
                case XLS, XLSX -> excelParser.parse(in, limits);
            };
        } catch (StatementObjectNotFoundException ex) {
            fail(s, "FILE_MISSING");
            return;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex); // transient: redeliver
        }

        if (parsed.transactions().isEmpty() && !parsed.errors().isEmpty()) {
            fail(s, fileLevelError(parsed.errors()).map(Enum::name).orElse("NO_VALID_ROWS"));
            return;
        }

        int skipped = parsed.errors().size();
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, Transaction> byFingerprint = new HashMap<>();
        Map<String, UUID> merchantIds = new HashMap<>();
        for (ParsedTransaction p : parsed.transactions()) {
            BigDecimal cents = p.amount().movePointRight(2);
            if (cents.precision() - cents.scale() > MAX_CENT_DIGITS) {
                skipped++;
                continue;
            }
            String base = TransactionFingerprint.baseKey(p.date(), cents, p.type(), p.rawDescription());
            int occurrence = occurrences.merge(base, 1, Integer::sum) - 1;
            String fp = TransactionFingerprint.of(s.getUserId(), p.date(), cents, p.type(), p.rawDescription(), occurrence);
            Transaction t = toTransaction(s, p, cents, fp);
            enrich(t, merchantIds);
            byFingerprint.put(fp, t);
        }

        Set<String> existing = byFingerprint.isEmpty() ? Set.of()
                : transactionRepository.findExistingFingerprints(s.getUserId(), byFingerprint.keySet());
        List<Transaction> fresh = new ArrayList<>();
        byFingerprint.forEach((fp, t) -> {
            if (!existing.contains(fp)) {
                fresh.add(t);
            }
        });
        transactionRepository.saveAll(fresh);

        s.setStatus(StatementStatus.COMPLETED);
        s.setCompletedAt(Instant.now());
        s.setProcessingError(skipped == 0 ? null : "SKIPPED_ROWS:" + skipped);
        s.onUpdate();
        statementRepository.save(s);
        log.info("Statement processed: statementId={} inserted={} alreadyPresent={} skipped={}",
                s.getId(), fresh.size(), byFingerprint.size() - fresh.size(), skipped);
    }

    private static Transaction toTransaction(Statement s, ParsedTransaction p, BigDecimal cents, String fingerprint) {
        Transaction t = new Transaction(s.getUserId(), p.rawDescription(), cents,
                p.date().atStartOfDay(ZoneOffset.UTC).toInstant(), p.type());
        t.setStatementId(s.getId());
        t.setTransactionFingerprint(fingerprint);
        return t;
    }

    /** Merchant normalisation (LLD 11); the raw description is never modified. */
    private void enrich(Transaction t, Map<String, UUID> merchantIds) {
        MerchantMatch m = merchantNormalizer.normalize(t.getRawDescription());
        t.setNormalizedDescription(m.normalizedDescription());
        if (m.canonicalName() != null) {
            t.setMerchantId(merchantIds.computeIfAbsent(m.canonicalName(), n -> merchantService.resolve(m).getId()));
            t.setMerchantConfidence(m.confidence());
        }
    }

    /** Whole-file failures are reported with row 0 (limits, unreadable) or as a missing header. */
    private static Optional<RowErrorCode> fileLevelError(List<RowError> errors) {
        return errors.stream()
                .filter(r -> r.rowNumber() == 0 || r.code() == RowErrorCode.MISSING_HEADER)
                .map(RowError::code)
                .findFirst();
    }

    private void fail(Statement s, String code) {
        s.setStatus(StatementStatus.FAILED);
        s.setProcessingError(code);
        s.onUpdate();
        statementRepository.save(s);
        log.info("Statement processing failed: statementId={} code={}", s.getId(), code);
    }

    private Optional<Envelope> envelope(String message) {
        try {
            JsonNode root = jsonMapper.readTree(message);
            if (!OutboxService.STATEMENT_UPLOADED.equals(root.path("eventType").asString(null))) {
                log.warn("Unexpected event type ignored");
                return Optional.empty();
            }
            if (root.path("eventVersion").asInt(-1) != SUPPORTED_VERSION) {
                log.warn("Unsupported event version ignored: version={}", root.path("eventVersion").asInt(-1));
                return Optional.empty();
            }
            return Optional.of(new Envelope(
                    UUID.fromString(root.path("eventId").asString()),
                    UUID.fromString(root.path("userId").asString()),
                    UUID.fromString(root.path("aggregateId").asString())));
        } catch (JacksonException | IllegalArgumentException ex) {
            log.warn("Malformed event ignored: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
