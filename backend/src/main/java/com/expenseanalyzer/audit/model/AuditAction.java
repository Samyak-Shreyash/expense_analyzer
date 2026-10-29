package com.expenseanalyzer.audit.model;

/** Audited actions. Add new values here rather than free-text actions. */
public enum AuditAction {
    AUTH_REGISTERED,
    AUTH_LOGIN_SUCCEEDED,
    AUTH_LOGIN_FAILED,
    STATEMENT_UPLOADED,
    STATEMENT_ACCESSED,
    STATEMENT_DELETED
}
