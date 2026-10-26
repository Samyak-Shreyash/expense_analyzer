# Issue #116 Plan: File Size, Type, and Signature Validation

Issue: #116

## Goal
Reusable `StatementFileValidator` that checks size, extension, declared MIME type and magic bytes of an uploaded statement file and throws a dedicated exception (mapped to a clear HTTP error) on failure. The upload endpoint (#229) will call it. Out of scope: the endpoint, storage, multipart wiring, malware scanning, and the upload security policy / malicious-upload test suites (#170, #172).

## Global Constraints
- Java 21, Spring Boot 4, Gradle Groovy; run from `backend/` with `./gradlew.bat`. Tests use H2, no Docker.
- Edit only `backend/src/`. Never touch `.env`. No Flyway migration (no schema change).
- Decision: custom magic-byte checks, NO Apache Tika (not in `backend/build.gradle`; three signatures do not justify a dependency). No new dependencies. The validator takes `(String filename, String contentType, long size, byte[] head)` so it does not depend on `MultipartFile`; the endpoint (#229) adapts.
- Config property: `app.statement.upload.max-size-bytes`, default `20971520` (20 MB), read with `@Value("${app.statement.upload.max-size-bytes:20971520}")` (same style as `auth/config/RateLimiter`). Add the key to `backend/src/main/resources/application.yml` under existing `app:`.
- Allowed extensions (case-insensitive, last extension only): `csv`, `pdf`, `xlsx`.
- Allowed declared MIME per extension (parameters such as `; charset=` stripped, case-insensitive):
  - csv: `text/csv`, `text/plain`, `application/csv`, `application/vnd.ms-excel`
  - pdf: `application/pdf`
  - xlsx: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Signatures: PDF starts with `%PDF-` (25 50 44 46 2D); XLSX starts with `50 4B 03 04`; CSV has no binary signature: the first 8192 bytes (`head`) must contain no `0x00` and be valid UTF-8 (a multibyte char truncated at the end of `head` is tolerated). Empty (0 byte) file rejected for all types.
- Reason codes (enum `Reason`): `EMPTY_FILE`, `FILE_TOO_LARGE`, `UNSUPPORTED_EXTENSION`, `MIME_TYPE_MISMATCH`, `SIGNATURE_MISMATCH`.
- Logging: `log.warn("Statement file rejected: reason={}", reason)` only. Never log filename, content or user data.
- Error messages are fixed strings per reason and never echo the filename or content. HTTP: `FILE_TOO_LARGE` -> 413, all others -> 400 (RFC 7807 `ProblemDetail`, with property `reason`).
- Placement: validator in `com.expenseanalyzer.statement.service`; exception + handler in `com.expenseanalyzer.common.exception` (dir exists with only `.gitkeep`). There is no existing `@ControllerAdvice` in the repo, so this creates the first one; keep it minimal (this exception only). `common/validation` is empty and unused (validator is statement-specific).

## Acceptance Criteria -> Test Mapping
| Criterion | Test (in `StatementFileValidatorTest` unless noted) |
|---|---|
| Reject files > 20 MB (configurable) | `rejectsFileOverLimit`, `acceptsFileExactlyAtLimit`, `respectsCustomLimit` |
| Validate extension | `rejectsUnsupportedExtension` (`.exe`, `.csv.exe`, no extension, null), `extensionIsCaseInsensitive` |
| Validate MIME type | `rejectsMimeMismatch` (.pdf with `text/csv`, null type), `ignoresMimeParameters` |
| Validate magic bytes | `rejectsPdfWithoutHeader`, `rejectsXlsxWithoutZipHeader`, `rejectsCsvWithNulBytes`, `rejectsCsvInvalidUtf8`, `rejectsExeRenamedToPdf`, `acceptsValidPdf/Xlsx/Csv`, `rejectsEmptyFile` |
| Clear error message | `messageDoesNotEchoFilename`; `GlobalExceptionHandlerTest` status/body |
| Log validation failures | `logsReasonOnlyNotFilename` (Logback `ListAppender`) |

## Tasks

### Task 1: Validator, exception and config
Files:
- Create `backend/src/main/java/com/expenseanalyzer/common/exception/InvalidFileException.java`
- Create `backend/src/main/java/com/expenseanalyzer/statement/service/StatementFileValidator.java`
- Edit `backend/src/main/resources/application.yml` (add under `app:`)
- Create `backend/src/test/java/com/expenseanalyzer/statement/service/StatementFileValidatorTest.java`

```java
public class InvalidFileException extends RuntimeException {
    public enum Reason { EMPTY_FILE, FILE_TOO_LARGE, UNSUPPORTED_EXTENSION, MIME_TYPE_MISMATCH, SIGNATURE_MISMATCH }
    private final Reason reason;
    public InvalidFileException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason getReason() { return reason; }
}
```
```yaml
app:
  statement:
    upload:
      max-size-bytes: ${STATEMENT_UPLOAD_MAX_BYTES:20971520}
```
Validator: `@Service`, constructor `StatementFileValidator(@Value("${app.statement.upload.max-size-bytes:20971520}") long maxSizeBytes)` so tests use `new StatementFileValidator(1024)`. Method `public void validate(String filename, String contentType, long size, byte[] head)`; `head` = first up to 8192 bytes read by the caller. Check order (cheapest first, content only read last): empty -> size -> extension -> MIME -> signature. Each failure goes through one private `reject(Reason, String message)` that logs the reason and throws. Null filename -> `UNSUPPORTED_EXTENSION`, null contentType -> `MIME_TYPE_MISMATCH`. Use a `Map<String, Set<String>>` for the MIME allow-list and a `switch` on extension for signatures. For CSV use `StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)` with `decode(buf, out, false)` (endOfInput=false tolerates a char cut at the buffer end) and fail on a malformed result.

Unit test cases (all fail before the change because the classes do not exist):
- Valid, no exception: `("a.pdf","application/pdf",100,"%PDF-1.7")`, `("a.xlsx", xlsxMime, 100, {0x50,0x4B,3,4})`, `("a.csv","text/csv; charset=utf-8",10,"date,amount\n")`, `("A.PDF","application/pdf",...)`.
- `size = limit+1` -> `FILE_TOO_LARGE`; `size == limit` passes; `new StatementFileValidator(1024)` rejects 1025.
- `a.exe`, `a.csv.exe`, `noext`, `null` filename -> `UNSUPPORTED_EXTENSION`.
- `("a.pdf","text/csv",..)` and null content type -> `MIME_TYPE_MISMATCH`.
- `a.pdf` + head `MZ\x90\x00` -> `SIGNATURE_MISMATCH`; `a.xlsx` + `%PDF-` head -> `SIGNATURE_MISMATCH`; `a.csv` with a `0x00` byte, and with invalid UTF-8 (`0xFF 0xFE 0xFA`) -> `SIGNATURE_MISMATCH`.
- size 0 -> `EMPTY_FILE`.
- Exception message for filename `secret-statement.pdf` does not contain `secret-statement`.
- Logging: attach a Logback `ListAppender<ILoggingEvent>` to the validator's logger, reject `secret-name.exe`; assert exactly one WARN event whose formatted message contains `UNSUPPORTED_EXTENSION` and not `secret-name`.

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.statement.service.StatementFileValidatorTest"`

### Task 2: HTTP mapping of the exception
Files:
- Create `backend/src/main/java/com/expenseanalyzer/common/exception/GlobalExceptionHandler.java`
- Create `backend/src/test/java/com/expenseanalyzer/common/exception/GlobalExceptionHandlerTest.java`

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFile(InvalidFileException e) {
        HttpStatus s = e.getReason() == InvalidFileException.Reason.FILE_TOO_LARGE
                ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST;
        ProblemDetail p = ProblemDetail.forStatusAndDetail(s, e.getMessage());
        p.setTitle("Invalid file");
        p.setProperty("reason", e.getReason().name());
        return ResponseEntity.status(s).body(p);
    }
}
```
Tests (plain unit, no Spring context): `FILE_TOO_LARGE` -> status 413, `detail == "msg"`, `getProperties().get("reason") == "FILE_TOO_LARGE"`; `UNSUPPORTED_EXTENSION` -> 400. Fail before the change (class missing). Then confirm existing security/context tests still pass via the full build (the advice only matches this exception).

Verify: `cd backend && ./gradlew.bat test --tests "com.expenseanalyzer.common.exception.GlobalExceptionHandlerTest"`, then `cd backend && ./gradlew.bat clean build`.
