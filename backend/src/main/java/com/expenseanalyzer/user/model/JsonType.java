package com.expenseanalyzer.user.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

/**
 * Converter for JSONB column.
 */
@Converter(autoApply = true)
public class JsonType implements AttributeConverter<Map<String, Object>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        return attribute != null ? attribute.toString() : null;
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbValue) {
        return dbValue != null ? new HashMap<String, Object>() : null;
    }
}
