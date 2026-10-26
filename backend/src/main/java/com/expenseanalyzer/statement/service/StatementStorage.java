package com.expenseanalyzer.statement.service;

public interface StatementStorage {

    /** Stores the bytes under the key, replacing any existing object. */
    void put(String key, byte[] bytes, String contentType);

    /** Throws StatementObjectNotFoundException when no object exists for the key. */
    byte[] get(String key);

    /** Idempotent: deleting a missing key succeeds. */
    void delete(String key);
}
