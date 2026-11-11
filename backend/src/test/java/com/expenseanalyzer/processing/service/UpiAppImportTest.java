package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.processing.parser.pdf.GPayPdf;
import com.expenseanalyzer.processing.repository.ProcessedEventRepository;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.model.StatementFormat;
import com.expenseanalyzer.statement.model.StatementSource;
import com.expenseanalyzer.statement.model.StatementStatus;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.S3StatementStorage;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** A payment in both a bank statement and a Google Pay export is stored once, whichever file comes first. */
@SpringBootTest
class UpiAppImportTest {

    private static final String BANK = """
            Date,Description,Amount
            2026-05-04,UPI/DR/612148248644/P2M/BLINKIT/YESB/blinkit@ybl,-1000.00
            2026-05-05,UPI/DR/612199990000/P2M/ZEPTO/YESB/zepto@ybl,-250.00
            """;

    @Autowired
    private StatementProcessor processor;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private S3StatementStorage storage;

    private final UUID userId = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        statementRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    private static byte[] gpay() throws IOException {
        List<String> lines = new ArrayList<>();
        lines.addAll(List.of(GPayPdf.row("04 May, 2026", "Paid to Blinkit", "1,000", "04:25 PM", "612148248644",
                "Paid by Axis Bank 6457")));
        lines.addAll(List.of(GPayPdf.row("06 May, 2026", "Paid to Hong's Kitchen", "372.50", "07:30 PM",
                "612100730574", "Paid by Axis Bank 6457")));
        lines.addAll(List.of(GPayPdf.row("07 May, 2026", "Self transfer to DBS Bank 5460", "5,000", "09:00 AM",
                "612355554444", "Paid by Axis Bank 6457")));
        return GPayPdf.of(lines.toArray(String[]::new));
    }

    private void importFile(StatementSource source, StatementFormat format, byte[] content) {
        Statement s = new Statement();
        s.setUserId(userId);
        s.setActive(true);
        s.setFormat(format);
        s.setSource(source);
        s.setStatus(StatementStatus.QUEUED);
        s.setFileName("file");
        s.setFileHash(UUID.randomUUID().toString().replace("-", ""));
        s.onCreate();
        s = statementRepository.saveAndFlush(s);
        s.setStorageKey(S3StatementStorage.keyFor(userId, s.getId()));
        s = statementRepository.saveAndFlush(s);
        when(storage.read(s.getStorageKey())).thenAnswer(inv -> new ByteArrayInputStream(content));
        processor.process("""
                {"eventId":"%s","eventType":"StatementUploaded","eventVersion":1,"occurredAt":"2026-01-01T00:00:00Z",
                 "producer":"statement-module","correlationId":"c","userId":"%s","aggregateId":"%s",
                 "payload":{"statementId":"%s","storageKey":"%s","format":"%s","fileHash":"x"}}
                """.formatted(UUID.randomUUID(), userId, s.getId(), s.getId(), s.getStorageKey(), format));
        assertThat(statementRepository.findById(s.getId()).orElseThrow().getStatus())
                .isEqualTo(StatementStatus.COMPLETED);
    }

    private List<Transaction> rowsWithRef(String ref) {
        return transactionRepository.findByUserIdAndUpiRefIn(userId, List.of(ref));
    }

    @Test
    void googlePayAfterBankKeepsTheBankRowOnly() throws IOException {
        importFile(StatementSource.BANK, StatementFormat.CSV, BANK.getBytes(StandardCharsets.UTF_8));
        importFile(StatementSource.GPAY, StatementFormat.PDF, gpay());

        assertThat(rowsWithRef("612148248644")).singleElement()
                .extracting(Transaction::getRawDescription).asString().startsWith("UPI/DR/612148248644");
        assertThat(rowsWithRef("612100730574")).hasSize(1); // only in Google Pay: imported
        assertThat(transactionRepository.findAll()).hasSize(4);
    }

    @Test
    void bankAfterGooglePayReplacesTheAppRowAndKeepsItsCategory() throws IOException {
        importFile(StatementSource.GPAY, StatementFormat.PDF, gpay());
        Transaction app = rowsWithRef("612148248644").getFirst();
        UUID category = UUID.randomUUID();
        app.setCategoryId(category);
        transactionRepository.saveAndFlush(app);

        importFile(StatementSource.BANK, StatementFormat.CSV, BANK.getBytes(StandardCharsets.UTF_8));

        Transaction kept = rowsWithRef("612148248644").getFirst();
        assertThat(rowsWithRef("612148248644")).hasSize(1);
        assertThat(kept.getRawDescription()).startsWith("UPI/DR/612148248644");
        assertThat(kept.getCategoryId()).isEqualTo(category);
        assertThat(transactionRepository.findAll()).hasSize(4);
    }

    @Test
    void differentAmountsWithTheSameReferenceAreBothKept() throws IOException {
        importFile(StatementSource.BANK, StatementFormat.CSV, """
                Date,Description,Amount
                2026-05-04,UPI/DR/612148248644/P2M/BLINKIT/YESB/blinkit@ybl,-999.00
                """.getBytes(StandardCharsets.UTF_8));
        importFile(StatementSource.GPAY, StatementFormat.PDF, gpay());

        assertThat(rowsWithRef("612148248644")).hasSize(2);
    }

    @Test
    void upiCirclePaymentMatchesTheBankRow() throws IOException {
        importFile(StatementSource.BANK, StatementFormat.CSV, """
                Date,Description,Amount
                2026-05-08,UPI/P2M/630490857431/Parampara/YES BANK/UPI//P2V/,-180.00
                """.getBytes(StandardCharsets.UTF_8));
        importFile(StatementSource.GPAY, StatementFormat.PDF, GPayPdf.of(GPayPdf.row("08 May, 2026",
                "Paid to Parampara the mithai shop sangareddy", "180", "06:12 PM", "630490857431",
                "Paid by Axis Bank 6457 | Paid for trisha mallick with UPI Circle")));

        assertThat(rowsWithRef("630490857431")).singleElement()
                .extracting(Transaction::getRawDescription).asString().startsWith("UPI/P2M/630490857431");
    }

    @Test
    void selfTransferFromGooglePayIsATransfer() throws IOException {
        importFile(StatementSource.GPAY, StatementFormat.PDF, gpay());

        assertThat(rowsWithRef("612355554444")).singleElement().extracting(Transaction::isTransfer).isEqualTo(true);
        assertThat(rowsWithRef("612100730574")).singleElement().extracting(Transaction::isTransfer).isEqualTo(false);
    }
}
