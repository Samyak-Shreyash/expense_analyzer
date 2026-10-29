package com.expenseanalyzer.statement.service;

import java.io.InputStream;
import java.util.UUID;

/** Private storage for original statement files. The database keeps only the key, never the bytes. */
public interface StatementStorage {

    static String keyFor(UUID userId, UUID statementId) {
        return "users/" + userId + "/statements/" + statementId;
    }

    void store(String key, InputStream content, long size, String contentType);

    /** @throws StatementObjectNotFoundException if no object exists for the key */
    InputStream read(String key);

    /** Deleting a missing object is a no-op. */
    void delete(String key);
}
