# [PROC-002] CSV/Excel Parser Implementation Plan

Issue: #111

## Goal
Pure parser services that turn a CSV or XLSX `InputStream` into `ParsedStatement` (valid rows + per-row errors). Invalid rows are reported, never thrown, and never abort the file. No persistence, no upload endpoint, no Kafka, no Transaction entity mapping (later issue).

Decisions:
- Libraries: add `org.apache.commons:commons-csv:1.12.0` (correct RFC4180 quoted fields/embedded newlines) and `org.apache.poi:poi-ooxml:5.3.0` (XLSX). OpenCSV not used. Neither is in build.gradle today. Only `.xlsx` (no legacy `.xls`).
- "Known bank formats" = ONE generic header-based format (below). Per-bank mappings are out of scope; the shared `StatementRowMapper` is the single place to extend later.
- Uses existing empty packages `processing/parser/common`, `processing/parser/csv`, `processing/parser/excel`. No `StatementParser` interface (YAGNI; add when PDF parser lands).
- Parsers do not log. Row errors carry row number + field name + code only, never cell values.

## Global Constraints
- Base package: `com.expenseanalyzer.processing.parser`.
- Header row = first non-empty row. Header match: trimmed, case-insensitive. Columns by name, any order, extra columns ignored.
  - Required: `Date`, `Description`.
  - Amount: either `Debit` and/or `Credit`, OR `Amount`. If neither is present (or Date/Description missing) the file yields zero rows and one `RowError(1, MISSING_HEADER)` (not an exception).
- Date formats accepted (strict, tried in order): `yyyy-MM-dd`, `dd/MM/yyyy`, `dd-MM-yyyy`. XLSX date-formatted numeric cells are converted to ISO `yyyy-MM-dd` before mapping. Anything else -> `INVALID_DATE`. `MM/dd/yyyy` deliberately unsupported (ambiguous; do not silently infer).
- Amount: `new BigDecimal(s)` after trim and removing `,` thousands separators; no double in the CSV path (XLSX numeric cell: `BigDecimal.valueOf(double).toPlainString()` then same path). Currency symbols -> `INVALID_AMOUNT`.
- Sign convention:
  - Debit/Credit columns: `Debit` value present -> `DEBIT`; `Credit` value present -> `CREDIT`; both non-blank, or both blank -> `AMBIGUOUS_DIRECTION`. Stored amount is absolute magnitude.
  - Single `Amount` column: negative -> `DEBIT`, positive -> `CREDIT`; zero -> `INVALID_AMOUNT`. Stored as absolute magnitude.
  - `ParsedTransaction.amount` always > 0, input scale preserved. `type` is the String `"DEBIT"`/`"CREDIT"` (matches `Transaction.type`).
- `rawDescription` = cell text untouched apart from outer-whitespace trim; blank -> `MISSING_DESCRIPTION`; over 2048 chars (Transaction.raw_description) -> `DESCRIPTION_TOO_LONG`.
- Fully blank rows are skipped silently (no row, no error).
- Row numbers are 1-based file rows (CSV: record number, header = 1; XLSX: sheet row number). XLSX: first sheet only.
- `RowErrorCode`: `MISSING_HEADER, INVALID_DATE, INVALID_AMOUNT, AMBIGUOUS_DIRECTION, MISSING_DESCRIPTION, DESCRIPTION_TOO_LONG, MALFORMED_ROW, UNREADABLE_FILE`.
- Unreadable/corrupt input -> zero rows and `RowError(0, UNREADABLE_FILE)`; no exception escapes public `parse`.
- Java 21 records, no Lombok; parsers are Spring `@Component`.

## Acceptance criteria -> tests
| Criterion | Test |
|---|---|
| Parses CSV with known format | `CsvStatementParserTest.parsesDebitCreditColumns`, `parsesSingleAmountColumn`, `handlesQuotedFieldsWithCommasAndNewlines`, `headersCaseInsensitiveAnyOrder` |
| Parses Excel | `ExcelStatementParserTest.parsesDebitCreditSheet`, `parsesDateFormattedCellsAndNumericAmounts` |
| Extracts date, description, amount, debit/credit | the above assert `date`, `rawDescription`, `amount`, `type` |
| Invalid rows handled gracefully | `CsvStatementParserTest.reportsInvalidRowsAndKeepsValidOnes`, `missingRequiredHeaderReturnsErrorNotException`; `ExcelStatementParserTest.reportsInvalidRowsAndKeepsValidOnes`, `corruptFileReturnsUnreadableError` |
| Mapping rules (dates, sign, ambiguity) | `StatementRowMapperTest.*` |

## Tasks

### Task 1: Dependencies, result model and shared row mapper
Files:
- Modify `backend/build.gradle`: add `implementation 'org.apache.commons:commons-csv:1.12.0'` and `implementation 'org.apache.poi:poi-ooxml:5.3.0'`.
- Create `backend/src/main/java/com/expenseanalyzer/processing/parser/common/ParsedTransaction.java`: `record ParsedTransaction(int rowNumber, LocalDate date, String rawDescription, BigDecimal amount, String type)`.
- Create `.../parser/common/RowErrorCode.java` (enum above) and `RowError.java`: `record RowError(int rowNumber, RowErrorCode code, String field)` (field nullable; never a cell value).
- Create `.../parser/common/ParsedStatement.java`: `record ParsedStatement(List<ParsedTransaction> transactions, List<RowError> errors)` with static `failed(RowError)`.
- Create `.../parser/common/StatementRowMapper.java`: constructor takes header `List<String>`; `boolean hasValidHeaders()`; `Object map(int rowNumber, List<String> cells)` or a small sealed result returning `ParsedTransaction` or `RowError`. Applies all Global Constraints.
- Test: `backend/src/test/java/com/expenseanalyzer/processing/parser/common/StatementRowMapperTest.java` (plain unit test, no Spring).

Test cases:
1. Headers `[" date ","DESCRIPTION","Debit","Credit"]` valid; `["Date","Description"]` -> `hasValidHeaders()==false`.
2. Debit `1,250.50` -> DEBIT, amount equals `new BigDecimal("1250.50")` (scale 2 kept).
3. Credit `100` -> CREDIT `100`.
4. Amount column `-45.10` -> DEBIT `45.10`; `45.10` -> CREDIT `45.10`.
5. Dates `31/12/2025`, `31-12-2025`, `2025-12-31` -> `LocalDate.of(2025,12,31)`; `12/31/2025` and `2025-13-01` -> INVALID_DATE.
6. Debit and Credit both `10` -> AMBIGUOUS_DIRECTION; both blank -> AMBIGUOUS_DIRECTION.
7. Amount `abc`, `$5`, `0` -> INVALID_AMOUNT.
8. Blank description -> MISSING_DESCRIPTION; 2049-char description -> DESCRIPTION_TOO_LONG; `"  AMZN*MKTP  IN "` -> `"AMZN*MKTP  IN"` (inner spacing preserved).
9. Cell list shorter than header treated as blank cells, no exception.

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.processing.parser.common.StatementRowMapperTest"`

### Task 2: CSV parser
Files:
- Create `backend/src/main/java/com/expenseanalyzer/processing/parser/csv/CsvStatementParser.java`: `@Component`, `ParsedStatement parse(InputStream in)`. commons-csv `CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).setTrim(true).get()` (or `.build()` if `get()` unavailable), UTF-8 reader, strip leading BOM. First record = headers -> `StatementRowMapper`; each later record -> `map(record.getRecordNumber(), cells)`. `IOException`/`UncheckedIOException`/`IllegalArgumentException` while reading -> `UNREADABLE_FILE`. Caller owns the stream.
- Test: `backend/src/test/java/com/expenseanalyzer/processing/parser/csv/CsvStatementParserTest.java`, inline string fixtures via `ByteArrayInputStream`.

Test cases:
1. `parsesDebitCreditColumns`: `Date,Description,Debit,Credit` with `2025-01-05,Coffee Shop,4.50,` and `2025-01-06,Salary,,3000.00` -> DEBIT 4.50 / CREDIT 3000.00, no errors.
2. `parsesSingleAmountColumn`: `Date,Description,Amount` with `-12.00` and `12.00`.
3. `headersCaseInsensitiveAnyOrder`: `amount,DESCRIPTION,date,Balance` (Balance ignored).
4. `handlesQuotedFieldsWithCommasAndNewlines`: description `"ACME, Inc.\nPayment"` preserved exactly.
5. `reportsInvalidRowsAndKeepsValidOnes`: 5 data rows, row 3 bad date, row 5 amount `abc`, plus a blank line -> 3 txns, errors `[(3,INVALID_DATE),(5,INVALID_AMOUNT)]`.
6. `missingRequiredHeaderReturnsErrorNotException`: header `Foo,Bar` -> 0 txns, one MISSING_HEADER.
7. `emptyInputReturnsMissingHeader`.
8. `errorsDoNotContainCellValues`: bad amount `SECRET123`; `errors.toString()` does not contain it.
9. UTF-8 BOM before `Date` still recognised.

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.processing.parser.csv.CsvStatementParserTest"`

### Task 3: Excel (XLSX) parser
Files:
- Create `backend/src/main/java/com/expenseanalyzer/processing/parser/excel/ExcelStatementParser.java`: `@Component`, `ParsedStatement parse(InputStream in)`. `XSSFWorkbook` in try-with-resources, first sheet, first non-empty row = headers. Cell to String: STRING trimmed; NUMERIC date-formatted (`DateUtil.isCellDateFormatted`) -> `getLocalDateTimeCellValue().toLocalDate().toString()`; other NUMERIC -> `BigDecimal.valueOf(v).toPlainString()`; FORMULA -> cached result type; BLANK -> "". Row number = `row.getRowNum()+1`. Any failure opening the workbook (IOException, POI/runtime exceptions on corrupt input) -> `UNREADABLE_FILE`.
- Test: `backend/src/test/java/com/expenseanalyzer/processing/parser/excel/ExcelStatementParserTest.java`. Workbooks built in-test with `XSSFWorkbook`, written to `ByteArrayOutputStream`; helper `workbook(Object[][] rows)` writes String / Double / LocalDate (date `CellStyle` with format `yyyy-mm-dd`).

Test cases:
1. `parsesDebitCreditSheet`: header + 2 rows, string dates `05/01/2025`, string amounts.
2. `parsesDateFormattedCellsAndNumericAmounts`: real Excel date cell, numeric Debit `1234.5` -> amount `1234.5`, DEBIT, correct date (tests run in UTC).
3. `reportsInvalidRowsAndKeepsValidOnes`: bad-date row and non-numeric-amount row reported with correct 1-based sheet row numbers; valid rows kept.
4. `skipsBlankRows`.
5. `missingRequiredHeaderReturnsError`: header `Foo,Bar` -> MISSING_HEADER.
6. `corruptFileReturnsUnreadableError`: `"not a zip".getBytes()` -> 0 txns, UNREADABLE_FILE, no exception.
7. `readsFirstSheetOnly`.

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.processing.parser.excel.ExcelStatementParserTest"`, then final `cd backend && ./gradlew.bat clean build`.

## Execution record (from SDD ledger)

Spec: GitHub issue #111 acceptance criteria (no separate spec doc).
Preflight scan:
| Pair/Task | Produces/consumes | Finding |
|---|---|---|
| T1->T2, T1->T3 | T1 adds deps + ParsedTransaction/RowError/ParsedStatement/StatementRowMapper; T2 CSV and T3 XLSX parsers consume them | consistent |
| T2<->T3 | independent parsers sharing mapper; no shared file edits | ok |
| T1/T2/T3 self | ok | ok |
Task 1: dispatched (BASE 1db8188)
Task 1: minor (deferred): map() returns Object; negative Debit/Credit silently abs()'d; catch RuntimeException too broad; ParsedStatement lists not copied; test gaps; build.gradle deps uncommented
Task 1: cannot-verify (BOM on first header) — Ruling: carry into Task 2 CSV parser (strip BOM there) — cost if wrong: BOM CSVs fail header detection
Task 1: cannot-verify (new deps compile under Boot 4) — resolved: implementer's full test run compiled with them; clean build at finish
Task 1: fix round 1 dispatched (FIX_BASE 8be44ae) — Important: lenient BigDecimal parsing (sci-notation, loose comma strip)
Task 1: fix round 1/5 (1 addressed, 0 open — lenient BigDecimal parsing; commits 8be44ae..19dbf84)
Task 1: minor (deferred): no Unicode-digit test; leading-zero grouping (01,234) accepted; 1.000 rejected; PLAIN_AMOUNT declared mid-class
Task 1: complete (commits 1db8188..19dbf84, review clean)
Task 2: dispatched (BASE 19dbf84)
Task 2: minor (deferred): BOM before a quoted first header not stripped (strip at stream level); mid-file malformed record discards all rows, untested; FQ Tuple.tuple
Task 2: complete (commits 19dbf84..8260785, review clean)
Task 3: dispatched (BASE 8260785)
Task 3: minor (deferred): catch(Exception) wraps whole sheet loop (narrow to workbook open); whole-number cells render "1001.0"; numeric 0 in unused Debit/Credit → AMBIGUOUS_DIRECTION; no FORMULA/empty-sheet/negative-amount tests; Javadoc stream ownership
Task 3: note for upload (#229): POI XSSF builds full DOM — rely on upload size cap; optionally ZipSecureFile.setMaxEntrySize
Task 3: cannot-verify (clean build) — resolved at finish
Task 3: complete (commits 8260785..1b016a8, review clean)
Final review: 1 Important (zero in unused Debit/Credit → AMBIGUOUS_DIRECTION) + recommended Minor (XLSX whole numbers render 1001.0) — final fix wave dispatched (FIX_BASE 1b016a8)
Final fix wave: 2 addressed, 0 open (commits 1b016a8..443224a); re-review clean
Final review: parked — "0.000" in unused column not blanked (scale>2 rejected) — Ruling: edge case, row reported not thrown — cost if wrong: rare 3-decimal-zero exports get AMBIGUOUS_DIRECTION
