package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.processing.repository.ProcessedEventRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementObjectNotFoundException;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "app.parser.max-rows=100")
class StatementProcessorTest {

    private static final String CSV = """
            Date,Description,Amount
            2026-01-02,Coffee Shop,-4.50
            2026-01-03,Salary,50000.00
            """;

    @Autowired
    private StatementProcessor processor;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;
    @Autowired
    private MerchantRepository merchantRepository;
    @Autowired
    private PersonRepository personRepository;

    @MockitoBean
    private StatementStorage storage;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        processedEventRepository.deleteAll();
        merchantRepository.deleteAll();
        personRepository.deleteAll();
    }

    private Statement queued(UUID userId) {
        Statement s = new Statement();
        s.setUserId(userId);
        s.setActive(true);
        s.setFormat(StatementFormat.CSV);
        s.setStatus(StatementStatus.QUEUED);
        s.setFileName("statement.csv");
        s.setFileHash(UUID.randomUUID().toString().replace("-", ""));
        s.onCreate();
        s = statementRepository.saveAndFlush(s);
        s.setStorageKey(StatementStorage.keyFor(userId, s.getId()));
        return statementRepository.saveAndFlush(s);
    }

    private void fileContains(Statement s, String csv) {
        when(storage.read(s.getStorageKey()))
                .thenAnswer(inv -> new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private static String event(UUID eventId, Statement s, int version) {
        return """
                {"eventId":"%s","eventType":"StatementUploaded","eventVersion":%d,"occurredAt":"2026-01-01T00:00:00Z",
                 "producer":"statement-module","correlationId":"corr-1","userId":"%s","aggregateId":"%s",
                 "payload":{"statementId":"%s","storageKey":"%s","format":"CSV","fileHash":"x"}}
                """.formatted(eventId, version, s.getUserId(), s.getId(), s.getId(), s.getStorageKey());
    }

    private static String event(Statement s) {
        return event(UUID.randomUUID(), s, 1);
    }

    private Statement reload(Statement s) {
        return statementRepository.findById(s.getId()).orElseThrow();
    }

    @Test
    void persistsParsedTransactionsAndCompletesStatement() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);

        processor.process(event(s));

        List<Transaction> txns = transactionRepository.findByUserIdAndStatementId(s.getUserId(), s.getId());
        assertThat(txns).hasSize(2);
        Transaction coffee = txns.stream().filter(t -> t.getRawDescription().equals("Coffee Shop")).findFirst().orElseThrow();
        assertThat(coffee.getAmountCents()).isEqualByComparingTo("450");
        assertThat(coffee.getAmount()).isEqualByComparingTo("4.50");
        assertThat(coffee.getType()).isEqualTo("DEBIT");
        assertThat(coffee.getCurrency()).isEqualTo("INR");
        assertThat(coffee.getDate()).isEqualTo(LocalDate.of(2026, 1, 2).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(coffee.getTransactionFingerprint()).hasSize(64);

        Statement done = reload(s);
        assertThat(done.getStatus()).isEqualTo(StatementStatus.COMPLETED);
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(done.getProcessingError()).isNull();
    }

    @Test
    void importedTransactionsAreNormalizedAndLinkedToCanonicalMerchants() {
        UUID user = UUID.randomUUID();
        Statement first = queued(user);
        fileContains(first, """
                Date,Description,Amount
                2026-01-02,UPI-SWIGGY-ORDER-12345678,-450.00
                2026-01-03,UPI/9876543210@ybl/RAMESH KUMAR,-200.00
                """);
        Statement second = queued(user);
        fileContains(second, "Date,Description,Amount\n2026-02-02,POS 4521XXXX1234 SWIGGY BANGALORE,-300.00\n");

        processor.process(event(first));
        processor.process(event(second));

        List<Transaction> txns = transactionRepository.findByUserId(user);
        Transaction swiggy = txns.stream().filter(t -> t.getRawDescription().startsWith("UPI-SWIGGY")).findFirst().orElseThrow();
        Transaction person = txns.stream().filter(t -> t.getRawDescription().contains("RAMESH")).findFirst().orElseThrow();
        Transaction swiggyPos = txns.stream().filter(t -> t.getRawDescription().startsWith("POS")).findFirst().orElseThrow();

        assertThat(swiggy.getRawDescription()).isEqualTo("UPI-SWIGGY-ORDER-12345678"); // raw preserved
        assertThat(swiggy.getNormalizedDescription()).isEqualTo("SWIGGY ORDER");
        assertThat(swiggy.getMerchantId()).isNotNull();
        assertThat(swiggy.getMerchantConfidence()).isEqualByComparingTo("0.95");
        assertThat(swiggyPos.getMerchantId()).isEqualTo(swiggy.getMerchantId());
        assertThat(merchantRepository.findById(swiggy.getMerchantId()).orElseThrow().getCanonicalName()).isEqualTo("Swiggy");
        assertThat(merchantRepository.count()).isEqualTo(1);

        assertThat(person.getMerchantId()).isNull();
        assertThat(person.getMerchantConfidence()).isNull();
        assertThat(person.getNormalizedDescription()).isEqualTo("RAMESH KUMAR");
    }

    @Test
    void upiTransfersToPeopleCreateOnePersonPerUserAndNoMerchant() {
        UUID user = UUID.randomUUID();
        Statement s = queued(user);
        fileContains(s, """
                Date,Description,Amount
                2026-01-02,UPI/P2A/412345678901/RAHUL SHARMA/HDFC BANK/rent,-15000.00
                2026-01-03,UPI/P2P/412345678902/rahul sharma/AXIS BANK/dinner,-500.00
                2026-01-04,UPI/P2M/412345678903/SWIGGY/YES BANK/order,-300.00
                """);

        processor.process(event(s));

        List<Person> people = personRepository.findByUserIdOrderByNameAsc(user);
        assertThat(people).extracting(Person::getName).containsExactly("Rahul Sharma");
        List<Transaction> txns = transactionRepository.findByUserId(user);
        assertThat(txns).filteredOn(t -> t.getRawDescription().contains("/P2A/") || t.getRawDescription().contains("/P2P/"))
                .hasSize(2).allSatisfy(t -> {
                    assertThat(t.getPersonId()).isEqualTo(people.get(0).getId());
                    assertThat(t.getMerchantId()).isNull();
                });
        Transaction p2m = txns.stream().filter(t -> t.getRawDescription().contains("/P2M/")).findFirst().orElseThrow();
        assertThat(p2m.getPersonId()).isNull();
        assertThat(p2m.getMerchantId()).isNotNull();

        Statement other = queued(UUID.randomUUID());
        fileContains(other, "Date,Description,Amount\n2026-01-02,UPI/P2A/412345678901/RAHUL SHARMA/HDFC BANK/rent,-1.00\n");
        processor.process(event(other));
        assertThat(personRepository.count()).isEqualTo(2);
    }

    @Test
    void duplicateDeliveryOfSameEventIsProcessedOnce() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);
        String e = event(s);

        processor.process(e);
        processor.process(e);

        assertThat(transactionRepository.count()).isEqualTo(2);
        verify(storage, times(1)).read(anyString());
    }

    @Test
    void concurrentDeliveryOfSameEventConvergesAfterRetry() throws Exception {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);
        String e = event(s);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> runs = List.of(
                    pool.submit(() -> { start.await(); processor.process(e); return null; }),
                    pool.submit(() -> { start.await(); processor.process(e); return null; }));
            start.countDown();
            for (Future<?> run : runs) {
                try {
                    run.get();
                } catch (ExecutionException loser) {
                    // the losing attempt hits a unique constraint and rolls back; Kafka would redeliver
                }
            }
        }
        processor.process(e); // redelivery of whichever attempt failed

        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.COMPLETED);
    }

    @Test
    void replayForCompletedStatementCreatesNoDuplicates() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);

        processor.process(event(s));
        processor.process(event(s)); // new eventId, same statement

        assertThat(transactionRepository.count()).isEqualTo(2);
        verify(storage, times(1)).read(anyString());
    }

    @Test
    void reprocessingAfterLostCompletionConvergesToSameData() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);
        processor.process(event(s));

        Statement again = reload(s);
        again.setStatus(StatementStatus.QUEUED); // e.g. operator re-queued it
        statementRepository.saveAndFlush(again);
        processor.process(event(s));

        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.COMPLETED);
    }

    @Test
    void overlappingStatementsDoNotDuplicateSharedTransactions() {
        UUID user = UUID.randomUUID();
        Statement first = queued(user);
        fileContains(first, CSV);
        processor.process(event(first));

        Statement second = queued(user);
        fileContains(second, """
                Date,Description,Amount
                2026-01-03,Salary,50000.00
                2026-01-04,Groceries,-1200.00
                """);
        processor.process(event(second));

        assertThat(transactionRepository.findByUserId(user)).extracting(Transaction::getRawDescription)
                .containsExactlyInAnyOrder("Coffee Shop", "Salary", "Groceries");
    }

    @Test
    void sameTransactionsForDifferentUsersAreBothKept() {
        Statement a = queued(UUID.randomUUID());
        Statement b = queued(UUID.randomUUID());
        fileContains(a, CSV);
        fileContains(b, CSV);

        processor.process(event(a));
        processor.process(event(b));

        assertThat(transactionRepository.count()).isEqualTo(4);
    }

    @Test
    void legitimateRepeatedTransactionsInOneStatementAreKept() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, """
                Date,Description,Amount
                2026-01-02,Coffee,-4.50
                2026-01-02,Coffee,-4.50
                """);

        processor.process(event(s));

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void invalidRowsAreSkippedAndReported() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, """
                Date,Description,Amount
                2026-01-02,Coffee,-4.50
                not-a-date,Bad,-1.00
                """);

        processor.process(event(s));

        assertThat(transactionRepository.count()).isEqualTo(1);
        Statement done = reload(s);
        assertThat(done.getStatus()).isEqualTo(StatementStatus.COMPLETED);
        assertThat(done.getProcessingError()).isEqualTo("SKIPPED_ROWS:1");
    }

    @Test
    void amountTooLargeForStorageIsSkipped() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, """
                Date,Description,Amount
                2026-01-02,Huge,-1234567890.00
                2026-01-02,Coffee,-4.50
                """);

        processor.process(event(s));

        assertThat(transactionRepository.findByUserId(s.getUserId())).extracting(Transaction::getRawDescription)
                .containsExactly("Coffee");
        assertThat(reload(s).getProcessingError()).isEqualTo("SKIPPED_ROWS:1");
    }

    @Test
    void unreadableFileFailsStatementWithSafeCode() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, "just some text without headers\n");

        processor.process(event(s));

        Statement failed = reload(s);
        assertThat(failed.getStatus()).isEqualTo(StatementStatus.FAILED);
        assertThat(failed.getProcessingError()).isEqualTo("MISSING_HEADER");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void fileWithOnlyInvalidRowsFails() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, "Date,Description,Amount\nbad,X,-1\n");

        processor.process(event(s));

        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.FAILED);
        assertThat(reload(s).getProcessingError()).isEqualTo("NO_VALID_ROWS");
    }

    @Test
    void parserLimitBreachFailsStatement() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, "Date,Description,Amount\n" + "2026-01-02,A,-1\n".repeat(101));

        processor.process(event(s));

        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.FAILED);
        assertThat(reload(s).getProcessingError()).isEqualTo("TOO_MANY_ROWS");
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void missingStoredFileFailsStatement() {
        Statement s = queued(UUID.randomUUID());
        when(storage.read(s.getStorageKey())).thenThrow(new StatementObjectNotFoundException());

        processor.process(event(s));

        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.FAILED);
        assertThat(reload(s).getProcessingError()).isEqualTo("FILE_MISSING");
    }

    @Test
    void transientStorageFailurePropagatesSoTheEventIsRetried() {
        Statement s = queued(UUID.randomUUID());
        when(storage.read(s.getStorageKey()))
                .thenThrow(SdkClientException.create("down"))
                .thenAnswer(inv -> new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8)));
        String e = event(s);

        assertThatThrownBy(() -> processor.process(e)).isInstanceOf(SdkClientException.class);
        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.QUEUED);
        assertThat(processedEventRepository.count()).isZero();

        processor.process(e); // redelivery

        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.COMPLETED);
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void unknownEventVersionIsIgnored() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);

        processor.process(event(UUID.randomUUID(), s, 99));

        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.QUEUED);
        verify(storage, never()).read(anyString());
    }

    @Test
    void eventWhoseUserDoesNotOwnStatementIsIgnored() {
        Statement s = queued(UUID.randomUUID());
        fileContains(s, CSV);
        String forged = event(s).replace(s.getUserId().toString(), UUID.randomUUID().toString());

        processor.process(forged);

        assertThat(reload(s).getStatus()).isEqualTo(StatementStatus.QUEUED);
        verify(storage, never()).read(anyString());
    }

    @Test
    void eventForDeletedStatementIsAcknowledged() {
        Statement s = queued(UUID.randomUUID());
        String e = event(s);
        statementRepository.delete(s);

        processor.process(e);

        verify(storage, never()).read(anyString());
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void malformedMessageIsIgnored() {
        processor.process("{not json");
        processor.process("{\"eventType\":\"StatementUploaded\"}");

        verify(storage, never()).read(anyString());
    }
}
