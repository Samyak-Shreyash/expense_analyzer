package com.expenseanalyzer.processing.parser.pdf;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreditCardStatementParserTest {

    private final CreditCardStatementParser parser = new CreditCardStatementParser();

    private ParsedStatement parse(String... lines) throws IOException {
        return parser.parse(new ByteArrayInputStream(GPayPdf.of(lines)), ParserLimits.DEFAULTS);
    }

    @Test
    void readsCommonIssuerLayouts() throws IOException {
        ParsedStatement r = parse(
                "Credit Card Statement",
                "Statement Date 20/06/2026 Total Amount Due 12,345.00",
                "Date Transaction Description Amount (in Rs.)",
                "12/05/2026 SWIGGY BANGALORE 450.00",          // slash date, no suffix
                "13/05/2026 PAYMENT RECEIVED - THANK YOU 5,000.00 Cr",
                "14-05-26 AMAZON PAY INDIA 1,299.00 Dr",
                "15 May 2026 FLIPKART REFUND 299.00 CR",
                "16-May-26 UBER INDIA 25 312.50 D",           // reward points column before the amount
                "17/05/2026 | 18:42 ZOMATO + Rs. 120.00",      // HDFC style: time and leading + for credit
                "Page 1 of 1");

        assertThat(r.errors()).isEmpty();
        List<ParsedTransaction> t = r.transactions();
        assertThat(t).extracting(ParsedTransaction::type)
                .containsExactly("DEBIT", "CREDIT", "DEBIT", "CREDIT", "DEBIT", "CREDIT");
        assertThat(t).extracting(p -> p.amount().toPlainString())
                .containsExactly("450.00", "5000.00", "1299.00", "299.00", "312.50", "120.00");
        assertThat(t).extracting(ParsedTransaction::date).containsExactly(
                LocalDate.of(2026, 5, 12), LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 14),
                LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 16), LocalDate.of(2026, 5, 17));
        assertThat(t).extracting(ParsedTransaction::rawDescription).containsExactly(
                "SWIGGY BANGALORE", "PAYMENT RECEIVED - THANK YOU", "AMAZON PAY INDIA", "FLIPKART REFUND",
                "UBER INDIA 25", "ZOMATO");
    }

    @Test
    void invalidDateIsARowError() throws IOException {
        ParsedStatement r = parse("31/02/2026 SOMETHING 10.00", "01/03/2026 OTHER 20.00");

        assertThat(r.transactions()).hasSize(1);
        assertThat(r.errors()).singleElement().satisfies(e -> assertThat(e.code()).isEqualTo(RowErrorCode.INVALID_DATE));
    }

    @Test
    void noTransactionsIsUnreadable() throws IOException {
        ParsedStatement r = parse("Credit Card Statement", "Nothing here");

        assertThat(r.transactions()).isEmpty();
        assertThat(r.errors()).singleElement().satisfies(e -> assertThat(e.code()).isEqualTo(RowErrorCode.UNREADABLE_FILE));
    }

    @Test
    void billPaymentsAreRecognisedButRefundsAreNot() {
        assertThat(CreditCardStatementParser.isBillPayment("PAYMENT RECEIVED - THANK YOU")).isTrue();
        assertThat(CreditCardStatementParser.isBillPayment("BBPS PAYMENT RECEIVED")).isTrue();
        assertThat(CreditCardStatementParser.isBillPayment("AUTOPAY DEBIT")).isTrue();
        assertThat(CreditCardStatementParser.isBillPayment("FLIPKART REFUND")).isFalse();
        assertThat(CreditCardStatementParser.isBillPayment("CASHBACK")).isFalse();
    }
}
