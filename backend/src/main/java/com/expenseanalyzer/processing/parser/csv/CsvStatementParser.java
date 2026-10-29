package com.expenseanalyzer.processing.parser.csv;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
import com.expenseanalyzer.processing.parser.common.ParserLimits;
import com.expenseanalyzer.processing.parser.common.ParsedTransaction;
import com.expenseanalyzer.processing.parser.common.RowError;
import com.expenseanalyzer.processing.parser.common.RowErrorCode;
import com.expenseanalyzer.processing.parser.common.StatementRowMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** Parses a CSV bank statement. Caller owns (and closes) the stream. Never logs cell values. */
@Component
public class CsvStatementParser {

    private static final CSVFormat FORMAT =
            CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).setTrim(true).build();

    public ParsedStatement parse(InputStream in) {
        return parse(in, ParserLimits.DEFAULTS);
    }

    public ParsedStatement parse(InputStream in, ParserLimits limits) {
        try {
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            CSVParser csv = FORMAT.parse(reader); // not closed: would close caller's stream
            Iterator<CSVRecord> it = csv.iterator();
            // Bank exports (e.g. Axis) put account details above the header; skip rows until it appears.
            StatementRowMapper mapper = null;
            while (it.hasNext() && mapper == null) {
                List<String> header = new ArrayList<>(it.next().toList());
                if (!header.isEmpty() && header.get(0).startsWith("\uFEFF")) {
                    header.set(0, header.get(0).substring(1));
                }
                StatementRowMapper candidate = new StatementRowMapper(header);
                mapper = candidate.hasValidHeaders() ? candidate : null;
            }
            if (mapper == null) {
                return ParsedStatement.failed(new RowError(1, RowErrorCode.MISSING_HEADER, null));
            }
            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            ParserLimits.Budget budget = limits.new Budget();
            while (it.hasNext()) {
                CSVRecord rec = it.next();
                budget.checkRow(rec.size());
                Object r = mapper.map((int) rec.getRecordNumber(), rec.toList());
                if (r instanceof ParsedTransaction t) {
                    txns.add(t);
                } else {
                    errors.add((RowError) r);
                }
            }
            return new ParsedStatement(txns, errors);
        } catch (ParserLimits.LimitExceeded e) {
            return ParsedStatement.failed(new RowError(0, e.code(), null));
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
    }
}
