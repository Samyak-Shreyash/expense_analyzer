package com.expenseanalyzer.processing.parser.csv;

import com.expenseanalyzer.processing.parser.common.ParsedStatement;
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
        try {
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            CSVParser csv = FORMAT.parse(reader); // not closed: would close caller's stream
            Iterator<CSVRecord> it = csv.iterator();
            if (!it.hasNext()) {
                return ParsedStatement.failed(new RowError(1, RowErrorCode.MISSING_HEADER, null));
            }
            List<String> header = new ArrayList<>(it.next().toList());
            if (!header.isEmpty() && header.get(0).startsWith("\uFEFF")) {
                header.set(0, header.get(0).substring(1));
            }
            StatementRowMapper mapper = new StatementRowMapper(header);
            if (!mapper.hasValidHeaders()) {
                return ParsedStatement.failed(new RowError(1, RowErrorCode.MISSING_HEADER, null));
            }
            List<ParsedTransaction> txns = new ArrayList<>();
            List<RowError> errors = new ArrayList<>();
            while (it.hasNext()) {
                CSVRecord rec = it.next();
                Object r = mapper.map((int) rec.getRecordNumber(), rec.toList());
                if (r instanceof ParsedTransaction t) {
                    txns.add(t);
                } else {
                    errors.add((RowError) r);
                }
            }
            return new ParsedStatement(txns, errors);
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            return ParsedStatement.failed(new RowError(0, RowErrorCode.UNREADABLE_FILE, null));
        }
    }
}
