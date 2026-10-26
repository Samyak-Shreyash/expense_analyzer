package com.expenseanalyzer.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expenseanalyzer.common.exception.StatementObjectNotFoundException;
import org.junit.jupiter.api.Test;

class InMemoryStatementStorageTest {

    private final InMemoryStatementStorage storage = new InMemoryStatementStorage();

    @Test
    void getOfMissingKeyThrowsNotFound() {
        storage.put("statements/u/present", new byte[] {1}, "text/csv");
        assertThat(storage.get("statements/u/present")).containsExactly(1);

        assertThatThrownBy(() -> storage.get("statements/u/missing"))
                .isInstanceOf(StatementObjectNotFoundException.class);
    }

    @Test
    void deleteOfMissingKeyIsIdempotentAndRemovesPresentKey() {
        storage.put("statements/u/a", new byte[] {1}, "text/csv");

        assertThatCode(() -> storage.delete("statements/u/missing")).doesNotThrowAnyException();
        storage.delete("statements/u/a");
        assertThatCode(() -> storage.delete("statements/u/a")).doesNotThrowAnyException();
        assertThatThrownBy(() -> storage.get("statements/u/a")).isInstanceOf(StatementObjectNotFoundException.class);
    }
}
