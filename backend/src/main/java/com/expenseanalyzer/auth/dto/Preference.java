package com.expenseanalyzer.auth.dto;

/**
 * User preference values.
 * Note: This enum is used for preferences stored in the JSONB column of the users table.
 */
public enum Preference {
    INR, USD, EUR, // Currency options
    DARK, LIGHT,   // Theme options
    TRUE, FALSE;   // Boolean-like options (for notifications)

    /**
     * Get preference value for theme.
     */
    public static Preference getTheme(String value) {
        if ("dark".equalsIgnoreCase(value)) {
            return DARK;
        } else if ("light".equalsIgnoreCase(value)) {
            return LIGHT;
        }
        return DARK; // default
    }

    /**
     * Get preference value for currency.
     */
    public static Preference getCurrency(String value) {
        if ("INR".equalsIgnoreCase(value)) {
            return INR;
        } else if ("USD".equalsIgnoreCase(value)) {
            return USD;
        } else if ("EUR".equalsIgnoreCase(value)) {
            return EUR;
        }
        return INR; // default
    }

    /**
     * Get preference value for boolean-like options.
     */
    public static Preference getBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return TRUE;
        } else if ("false".equalsIgnoreCase(value)) {
            return FALSE;
        }
        return TRUE; // default
    }
}
