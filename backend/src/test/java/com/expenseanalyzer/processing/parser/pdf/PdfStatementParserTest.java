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
