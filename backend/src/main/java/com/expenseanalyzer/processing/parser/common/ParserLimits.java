package com.expenseanalyzer.processing.parser.common;

import java.time.Duration;

/**
 * Per-file parsing budget (SEC-016). Exceeding any limit fails the whole file: a partial
 * import would understate spending. File size is capped earlier, at upload.
 *
 * @param maxRows    data rows, excluding the header
 * @param maxColumns cells in any one row
 * @param timeout    wall-clock budget for one parse
 */
public record ParserLimits(int maxRows, int maxColumns, Duration timeout) {

    public static final ParserLimits DEFAULTS = new ParserLimits(50_000, 50, Duration.ofSeconds(30));

    /** Throws {@link LimitExceeded} once the budget is spent. */
    public final class Budget {
        private final long deadlineNanos = System.nanoTime() + timeout.toNanos();
        private int rows;

        public Budget() {
        }

        public void checkRow(int columns) {
            if (System.nanoTime() - deadlineNanos >= 0) {
                throw new LimitExceeded(RowErrorCode.PARSE_TIMEOUT);
            }
            if (columns > maxColumns) {
                throw new LimitExceeded(RowErrorCode.TOO_MANY_COLUMNS);
            }
            if (++rows > maxRows) {
                throw new LimitExceeded(RowErrorCode.TOO_MANY_ROWS);
            }
        }
    }

    public static final class LimitExceeded extends RuntimeException {
        private final RowErrorCode code;

        LimitExceeded(RowErrorCode code) {
            super(code.name(), null, false, false);
            this.code = code;
        }

        public RowErrorCode code() {
            return code;
        }
    }
}
