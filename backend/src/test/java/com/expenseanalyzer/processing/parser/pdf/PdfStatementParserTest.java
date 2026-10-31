package com.expenseanalyzer.processing.parser.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfStatementParserTest {

    private final PdfStatementParser parser = new PdfStatementParser();

    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(50, 750);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLineAtOffset(0, -14);
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private ParsedStatement parse(byte[] b) {
        return parser.parse(new ByteArrayInputStream(b));
    }

    @Test
    void balanceMovementGivesDirection() throws IOException {
        ParsedStatement r = parse(pdf(
                "Opening Balance 10,000.00",
                "01-10-2026 UPI/SWIGGY 450.00 9,550.00",
                "03-10-2026 NEFT/SALARY 85,000.00 94,550.00"));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).extracting(t -> t.rawDescription(), t -> t.type())
                .containsExactly(tuple("UPI/SWIGGY", "DEBIT"), tuple("NEFT/SALARY", "CREDIT"));
        assertThat(r.transactions().get(1).amount()).isEqualByComparingTo("85000.00");
    }

    @Test
    void firstRowWithoutOpeningBalanceIsAmbiguous() throws IOException {
        ParsedStatement r = parse(pdf(
                "01-10-2026 UPI/SWIGGY 450.00 9,550.00",
                "03-10-2026 NEFT/SALARY 85,000.00 94,550.00"));
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.AMBIGUOUS_DIRECTION);
        assertThat(r.transactions()).extracting(t -> t.type()).containsExactly("CREDIT");
    }

    @Test
    void multiLineRowsWithBranchCodeAreParsed() throws IOException {
        ParsedStatement r = parse(pdf(
                "Tran Date Chq No Particulars Debit Credit Balance Init.",
                "Br",
                "OPENING BALANCE 5000.00",
                "02-11-2025",
                "UPI/P2M/111111111111/Some Shop",
                "/Reques/TEST BANK",
                "",
                "100.00 4900.00 300",
                "02-11-2025",
                "UPI/P2M/222222222222/Grocer",
                "/UPIInt/OTHER BANK 50.00 4850.00 300",
                "03-11-2025",
                "NEFT/REF0001/EMPLOYER",
                "LIMITED/SALARY",
                "",
                "2000.00 6850.00 24"));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).extracting(t -> t.rawDescription(), t -> t.type())
                .containsExactly(
                        tuple("UPI/P2M/111111111111/Some Shop/Reques/TEST BANK", "DEBIT"),
                        tuple("UPI/P2M/222222222222/Grocer/UPIInt/OTHER BANK", "DEBIT"),
                        tuple("NEFT/REF0001/EMPLOYER LIMITED/SALARY", "CREDIT"));
        assertThat(r.transactions().get(2).amount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void wrappedUpiDescriptionsKeepEverySegment() throws IOException {
        ParsedStatement r = parse(pdf(
                "OPENING BALANCE 126494.14",
                "27-10-2026",
                "",
                "UPI/P2M/630067215224/CheQ Credit Card",
                "Repa/Pay/HDFC BANK LTD",
                "",
                "56066.97 70427.17 300",
                "",
                "28-10-2026",
                "",
                "UPI/P2A/630101997383/KUMAR SAURAV",
                "/UPI/ICICI Bank",
                "",
                "5000.00 65427.17 300",
                "",
                "28-10-2026",
                "",
                "UPI/P2M/666700503667/HYPERAPPS",
                "/RESTAU/AIRTEL PAYMENTS BANK 421.72 65005.45 300"));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).extracting(t -> t.rawDescription()).containsExactly(
                "UPI/P2M/630067215224/CheQ Credit Card Repa/Pay/HDFC BANK LTD",
                "UPI/P2A/630101997383/KUMAR SAURAV/UPI/ICICI Bank",
                "UPI/P2M/666700503667/HYPERAPPS/RESTAU/AIRTEL PAYMENTS BANK");
    }

    @Test
    void descriptionLinesAboveTheDatedLineBelongToThatRow() throws IOException {
        // Axis as PDFBox reads it: a wrapped row's first lines come before the line holding date + amounts.
        ParsedStatement r = parse(pdf(
                "Statement of Account No :123 for the period",
                "Tran Date Chq No Particulars Debit Credit Balance Init.",
                "Br",
                "OPENING BALANCE            5000.00",
                "UPI/P2M/111111111111/FUEL STATION               ",
                "30-10-2026 /UPI/YES BANK LIMITED YBS 100.00             4900.00 300",
                "UPI/P2M/222222222222/LAB",
                "TECHNOLOGI/UPIInt/INDUSIND BANK",
                "30-10-2026 LIMITE                50.00              4850.00 300",
                "31-10-2026 SINGLE LINE ROW 10.00 4840.00 300",
                "TRANSACTION TOTAL 160.00 0.00",
                "CLOSING BALANCE             4840.00"));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).extracting(t -> t.rawDescription()).containsExactly(
                "UPI/P2M/111111111111/FUEL STATION/UPI/YES BANK LIMITED YBS",
                "UPI/P2M/222222222222/LAB TECHNOLOGI/UPIInt/INDUSIND BANK LIMITE",
                "SINGLE LINE ROW");
    }

    @Test
    void singleLineRowWithBranchCodeIsParsed() throws IOException {
        ParsedStatement r = parse(pdf(
                "Opening Balance 1,000.00",
                "01-10-2026 UPI/SHOP 100.00 900.00 300"));
        assertThat(r.errors()).isEmpty();
        assertThat(r.transactions()).extracting(t -> t.rawDescription()).containsExactly("UPI/SHOP");
    }

    @Test
    void pdfWithoutTransactionsIsUnreadable() throws IOException {
        ParsedStatement r = parse(pdf("Hello world"));
        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.UNREADABLE_FILE);
    }

    @Test
    void nonPdfIsUnreadable() {
        ParsedStatement r = parse("not a pdf".getBytes(StandardCharsets.US_ASCII));
        assertThat(r.errors()).extracting(RowError::code).containsExactly(RowErrorCode.UNREADABLE_FILE);
    }
}
