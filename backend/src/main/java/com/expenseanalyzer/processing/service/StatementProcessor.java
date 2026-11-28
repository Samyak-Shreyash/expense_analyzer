package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.messaging.service.OutboxService;
import com.expenseanalyzer.processing.model.ProcessedEvent;
import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import com.expenseanalyzer.processing.parser.csv.CsvStatementParser;
import com.expenseanalyzer.processing.parser.excel.ExcelStatementParser;
import com.expenseanalyzer.processing.parser.pdf.CreditCardStatementParser;
import com.expenseanalyzer.processing.parser.pdf.GPayStatementParser;
import com.expenseanalyzer.processing.parser.pdf.PdfStatementParser;
import com.expenseanalyzer.processing.repository.ProcessedEventRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementSource;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementObjectNotFoundException;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.transaction.service.LoanService;
import com.expenseanalyzer.transaction.service.ReversalService;
import com.expenseanalyzer.transaction.service.TransferService;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    // amount_paise is numeric(12,2): at most 10 integer digits
    private static final int MAX_PAISE_DIGITS = 10;
    private static final Logger log = LoggerFactory.getLogger(StatementProcessor.class);

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final S3StatementStorage storage;
    private final CsvStatementParser csvParser;
    private final ExcelStatementParser excelParser;
    private final PdfStatementParser pdfParser;
    private final GPayStatementParser gpayParser;
    private final CreditCardStatementParser creditCardParser;
    private final JsonMapper jsonMapper;
    private final TransactionEnricher enricher;
    private final ParserLimits limits;
    private final TransferService transferService;
    private final ReversalService reversalService;
    private final EffectiveCategoryResolver categoryResolver;
    private final LoanService loanService;
    private final UserRepository userRepository;

    public StatementProcessor(StatementRepository statementRepository, TransactionRepository transactionRepository,
                              ProcessedEventRepository processedEventRepository, S3StatementStorage storage,
                              CsvStatementParser csvParser, ExcelStatementParser excelParser,
                              PdfStatementParser pdfParser, GPayStatementParser gpayParser,
                              CreditCardStatementParser creditCardParser,
                              JsonMapper jsonMapper,
                              TransactionEnricher enricher, TransferService transferService,
                              ReversalService reversalService,
                              EffectiveCategoryResolver categoryResolver, LoanService loanService,
                              UserRepository userRepository,
                              @Value("${app.parser.max-rows:50000}") int maxRows,
                              @Value("${app.parser.max-columns:50}") int maxColumns,
                              @Value("${app.parser.timeout:30s}") Duration timeout) {
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.processedEventRepository = processedEventRepository;
        this.storage = storage;
        this.csvParser = csvParser;
        this.excelParser = excelParser;
        this.pdfParser = pdfParser;
        this.gpayParser = gpayParser;
        this.creditCardParser = creditCardParser;
        this.jsonMapper = jsonMapper;
        this.enricher = enricher;
        this.transferService = transferService;
        this.reversalService = reversalService;
        this.categoryResolver = categoryResolver;
        this.loanService = loanService;
        this.userRepository = userRepository;
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
        if (s.getStorageKey() == null) {
            // The file is deleted only after a successful import: its rows are already in.
            s.setStatus(StatementStatus.COMPLETED);
            s.onUpdate();
            statementRepository.save(s);
            return;
        }
        ParsedStatement parsed;
        byte[] file;
        try (InputStream stored = storage.read(s.getStorageKey())) {
            file = stored.readAllBytes();
            InputStream in = new ByteArrayInputStream(file);
            parsed = switch (s.getFormat()) {
                case CSV -> csvParser.parse(in, limits);
                case XLS, XLSX -> excelParser.parse(in, limits);
                case PDF -> switch (s.getSource()) {
                    case GPAY -> gpayParser.parse(in, limits);
                    case CREDIT_CARD -> creditCardParser.parse(in, limits);
                    case BANK -> pdfParser.parse(in, limits);
                };
            };
        } catch (StatementObjectNotFoundException ex) {
            fail(s, "FILE_MISSING");
            return;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex); // transient: redeliver
        } catch (OutOfMemoryError ex) {
            log.error("Statement exhausted memory during parsing: statementId={}", s.getId());
            fail(s, "OUT_OF_MEMORY"); // deterministic: the same file would exhaust the heap again
            return;
        }

        if (parsed.transactions().isEmpty() && !parsed.errors().isEmpty()) {
            fail(s, fileLevelError(parsed.errors()).map(Enum::name).orElse("NO_VALID_ROWS"));
            return;
        }

        int skipped = parsed.errors().size();
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, Transaction> byFingerprint = new HashMap<>();
        for (ParsedTransaction p : parsed.transactions()) {
            BigDecimal paise = p.amount().movePointRight(2);
            if (paise.precision() - paise.scale() > MAX_PAISE_DIGITS) {
                skipped++;
                continue;
            }
            String base = TransactionFingerprint.baseKey(p.date(), paise, p.type(), p.rawDescription());
            int occurrence = occurrences.merge(base, 1, Integer::sum) - 1;
            String fp = TransactionFingerprint.of(s.getUserId(), p.date(), paise, p.type(), p.rawDescription(), occurrence);
            Transaction t = toTransaction(s, p, paise, fp);
            byFingerprint.put(fp, t);
        }

        enricher.enrich(byFingerprint.values(), s.getUserId());

        Set<String> existing = byFingerprint.isEmpty() ? Set.of()
                : transactionRepository.findExistingFingerprints(s.getUserId(), byFingerprint.keySet());
        List<Transaction> fresh = new ArrayList<>();
        byFingerprint.forEach((fp, t) -> {
            if (!existing.contains(fp)) {
                fresh.add(t);
            }
        });
        int merged = mergeUpiDuplicates(s, fresh);
        enricher.applyKeywordCategories(s.getUserId(), fresh);
        categoryResolver.markSelfAsTransfers(s.getUserId(), fresh);
        Set<UUID> loans = loanService.autoLink(s.getUserId(), fresh);
        transactionRepository.saveAll(fresh);
        loanService.recompute(loans);
        transferService.match(s.getUserId());
        reversalService.match(s.getUserId());

        s.setDetectedAccount(AccountDetector.lastFour(file, s.getFormat()).orElse(null));
        if (userRepository.findById(s.getUserId()).map(User::isStripStatementPii).orElse(true)) {
            deleteFileAfterCommit(s);
        }
        s.setStatus(StatementStatus.COMPLETED);
        s.setCompletedAt(Instant.now());
        s.setProcessingError(skipped == 0 ? null : "SKIPPED_ROWS:" + skipped);
        s.onUpdate();
        statementRepository.save(s);
        log.info("Statement processed: statementId={} inserted={} alreadyPresent={} skipped={} mergedUpi={}",
                s.getId(), fresh.size(), byFingerprint.size() - fresh.size() - merged, skipped, merged);
    }

    /** Only the parsed rows are kept. Deleted after commit, so a rolled-back import can still be redelivered. */
    private void deleteFileAfterCommit(Statement s) {
        String key = s.getStorageKey();
        s.setStorageKey(null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    private static Transaction toTransaction(Statement s, ParsedTransaction p, BigDecimal paise, String fingerprint) {
        Transaction t = new Transaction(s.getUserId(), p.rawDescription(), paise,
                p.date().atStartOfDay(ZoneOffset.UTC).toInstant(), p.type());
        t.setStatementId(s.getId());
        t.setTransactionFingerprint(fingerprint);
        t.setUpiRef(UpiReference.find(p.rawDescription()));
        if (s.getSource() == StatementSource.GPAY && p.rawDescription().startsWith(GPayStatementParser.SELF_TRANSFER)) {
            t.setTransfer(true); // between the user's own accounts: never spending or income
        }
        if (s.getSource() == StatementSource.CREDIT_CARD && "CREDIT".equals(p.type())
                && CreditCardStatementParser.isBillPayment(p.rawDescription())) {
            t.setTransfer(true); // card bill paid from the user's bank account: already counted there
        }
        return t;
    }

    /**
     * One payment can arrive twice: in a bank statement and in a UPI app's export, sharing a UPI reference. The bank
     * row always wins. Importing a UPI app file drops its rows already in a bank statement; importing a bank statement
     * replaces earlier UPI app rows. Either way the kept row takes the other's category where it has none, and the app
     * row's payee (merchant or person) when it has one, since the app names the payee where the bank often does not. A reference held by more than one row on either side, or rows whose
     * amount or direction differ, are left alone. Removes dropped rows from {@code fresh}; returns how many merged.
     */
    private int mergeUpiDuplicates(Statement s, List<Transaction> fresh) {
        Map<String, List<Transaction>> incoming = fresh.stream().filter(t -> t.getUpiRef() != null)
                .collect(Collectors.groupingBy(Transaction::getUpiRef));
        if (incoming.isEmpty()) {
            return 0;
        }
        List<Transaction> stored = transactionRepository.findByUserIdAndUpiRefIn(s.getUserId(), incoming.keySet());
        Map<UUID, StatementSource> sources = statementRepository.findAllById(stored.stream()
                        .map(Transaction::getStatementId).filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Statement::getId, Statement::getSource));
        Map<String, List<Transaction>> byRef = stored.stream().collect(Collectors.groupingBy(Transaction::getUpiRef));

        boolean fromApp = s.getSource() == StatementSource.GPAY;
        List<Transaction> dropped = new ArrayList<>();
        List<Transaction> updated = new ArrayList<>();
        List<Transaction> replaced = new ArrayList<>();
        incoming.forEach((ref, mine) -> {
            List<Transaction> theirs = byRef.getOrDefault(ref, List.of());
            if (mine.size() != 1 || theirs.size() != 1) {
                return;
            }
            Transaction in = mine.getFirst();
            Transaction old = theirs.getFirst();
            boolean oldFromApp = sources.getOrDefault(old.getStatementId(), StatementSource.BANK) == StatementSource.GPAY;
            if (fromApp == oldFromApp || !in.getType().equals(old.getType())
                    || in.getAmountPaise().compareTo(old.getAmountPaise()) != 0) {
                return;
            }
            if (fromApp) { // the bank row is already stored: keep it
                adoptLabels(old, in);
                old.onUpdate();
                updated.add(old);
                dropped.add(in);
            } else { // the new bank row replaces the app row
                adoptLabels(in, old);
                replaced.add(old);
            }
        });
        fresh.removeAll(dropped);
        transactionRepository.saveAll(updated);
        transactionRepository.deleteAll(replaced);
        transactionRepository.flush();
        return dropped.size() + replaced.size();
    }

    /**
     * Gives the kept bank row the app row's category where it has none, and its payee (merchant or person) if any;
     * else, for a transfer to a person, the person the app names.
     */
    private void adoptLabels(Transaction kept, Transaction app) {
        if (kept.getCategoryId() == null) {
            kept.setCategoryId(app.getCategoryId());
        }
        if (app.getMerchantId() != null || app.getPersonId() != null) {
            kept.setMerchantId(app.getMerchantId());
            kept.setMerchantConfidence(app.getMerchantConfidence());
            kept.setPersonId(app.getPersonId());
        } else {
            enricher.namePersonFromApp(kept, app.getRawDescription());
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
