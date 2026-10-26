package com.expenseanalyzer.statement;

import com.expenseanalyzer.common.exception.StatementObjectNotFoundException;
import com.expenseanalyzer.statement.service.StatementStorage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "memory")
public class InMemoryStatementStorage implements StatementStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        objects.put(key, bytes.clone());
    }

    @Override
    public byte[] get(String key) {
        byte[] bytes = objects.get(key);
        if (bytes == null) {
            throw new StatementObjectNotFoundException("Statement object not found", null);
        }
        return bytes.clone();
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }
}
