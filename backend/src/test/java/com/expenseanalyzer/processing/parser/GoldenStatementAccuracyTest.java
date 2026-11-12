package com.expenseanalyzer.processing.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.csv.CsvStatementParser;
import com.expenseanalyzer.processing.parser.excel.ExcelStatementParser;
import com.expenseanalyzer.processing.parser.pdf.PdfStatementParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

/**
 * Scores extraction accuracy over the golden corpus in {@code src/test/resources/golden-statements}.
 * Each of the 4 fields (date, description, type, amount) of each expected row counts once; a missing
 * row loses all 4. The PRD target is 98% for supported formats.
 */
class GoldenStatementAccuracyTest {

    private static final double TARGET = 0.98;

    private final CsvStatementParser csv = new CsvStatementParser();
    private final ExcelStatementParser excel = new ExcelStatementParser(100L * 1024 * 1024);
    private final PdfStatementParser pdf = new PdfStatementParser();

    private record Expected(LocalDate date, String description, String type, BigDecimal amount) {}

    @Test
    void everyFormatMeetsTheAccuracyTarget() throws Exception {
        Map<String, int[]> byFormat = new TreeMap<>(); // format -> {correct, total}
        List<String> mismatches = new ArrayList<>();

        for (Path statement : statements()) {
            String name = statement.getFileName().toString();
            String format = name.substring(name.lastIndexOf('.') + 1);
            Path expectedFile = statement.resolveSibling(name.substring(0, name.lastIndexOf('.')) + ".expected.csv");
            assertThat(expectedFile).as("expected output for " + name).exists();

            List<Expected> expected = readExpected(expectedFile);
            List<ParsedTransaction> actual = parse(format, statement).transactions();
            int[] score = byFormat.computeIfAbsent(format, f -> new int[2]);
            score[1] += expected.size() * 4;

            for (int i = 0; i < expected.size(); i++) {
                Expected e = expected.get(i);
                if (i >= actual.size()) {
                    mismatches.add(name + " row " + (i + 1) + ": missing");
                    continue;
                }
                ParsedTransaction a = actual.get(i);
                score[0] += field(mismatches, name, i, "date", e.date(), a.date())
                        + field(mismatches, name, i, "description", e.description(), a.rawDescription())
                        + field(mismatches, name, i, "type", e.type(), a.type())
                        + (a.amount() != null && e.amount().compareTo(a.amount()) == 0 ? 1
                                : field(mismatches, name, i, "amount", e.amount(), a.amount()));
            }
            if (actual.size() > expected.size()) {
                mismatches.add(name + ": " + (actual.size() - expected.size()) + " unexpected extra rows");
            }
        }

        assertThat(byFormat).as("golden corpus is empty").isNotEmpty();
        byFormat.forEach((format, s) -> {
            double accuracy = (double) s[0] / s[1];
            System.out.printf("golden accuracy %s: %.2f%% (%d/%d fields)%n", format, accuracy * 100, s[0], s[1]);
            assertThat(accuracy).as(format + " accuracy; mismatches: " + mismatches).isGreaterThanOrEqualTo(TARGET);
        });
    }

    private static int field(List<String> mismatches, String file, int row, String field, Object expected, Object actual) {
        if (expected.equals(actual)) {
            return 1;
        }
        mismatches.add(file + " row " + (row + 1) + " " + field + ": expected <" + expected + "> but was <" + actual + ">");
        return 0;
    }

    private ParsedStatement parse(String format, Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return switch (format) {
                case "csv" -> csv.parse(in);
                case "xlsx", "xls" -> excel.parse(in);
                case "pdf" -> pdf.parse(in);
                default -> throw new IllegalArgumentException("no parser for ." + format);
            };
        }
    }

    private static List<Path> statements() throws IOException, URISyntaxException {
        Path dir = Path.of(GoldenStatementAccuracyTest.class.getResource("/golden-statements").toURI());
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> {
                String n = p.getFileName().toString();
                return !n.endsWith(".expected.csv") && !n.endsWith(".md");
            }).sorted().toList();
        }
    }

    private static List<Expected> readExpected(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<Expected> rows = new ArrayList<>();
            for (CSVRecord rec : CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(r)) {
                rows.add(new Expected(LocalDate.parse(rec.get("date")), rec.get("description"),
                        rec.get("type"), new BigDecimal(rec.get("amount"))));
            }
            return rows;
        }
    }
}
