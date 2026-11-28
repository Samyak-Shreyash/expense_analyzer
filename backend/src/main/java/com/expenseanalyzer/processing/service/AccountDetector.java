package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.statement.model.StatementFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Finds the statement's own account number in its header and returns only the last four digits, so the full number is
 * never stored. Only the first {@link #HEADER} characters are searched: rows further down name other people's accounts.
 */
final class AccountDetector {

    private static final int HEADER = 2000;
    private static final Pattern ACCOUNT = Pattern.compile(
            "(?i)\\b(?:a/?c|acct|account)\\s*(?:no|number|num|#)?\\.?\\s*[:\\-]?\\s*([0-9X*]{2,}[0-9X* -]{0,22}[0-9]{4})\\b");

    private AccountDetector() {
    }

    // ponytail: Excel headers are not read; add a POI cell scan if XLS/XLSX users ask for suggestions.
    static Optional<String> lastFour(byte[] file, StatementFormat format) {
        String text = switch (format) {
            case PDF -> pdfHeader(file);
            case CSV -> new String(Arrays.copyOf(file, Math.min(file.length, HEADER)), StandardCharsets.UTF_8);
            case XLS, XLSX -> "";
        };
        return find(text);
    }

    static Optional<String> find(String text) {
        Matcher m = ACCOUNT.matcher(text.length() > HEADER ? text.substring(0, HEADER) : text);
        if (!m.find()) {
            return Optional.empty();
        }
        String digits = m.group(1).replaceAll("\\D", "");
        return Optional.of(digits.substring(digits.length() - 4));
    }

    private static String pdfHeader(byte[] file) {
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(1);
            return stripper.getText(doc);
        } catch (IOException e) {
            return ""; // a suggestion only: the import itself reports unreadable files
        }
    }
}
