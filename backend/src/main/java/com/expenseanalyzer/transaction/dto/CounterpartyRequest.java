package com.expenseanalyzer.transaction.dto;

/** Who a transaction's money went to: a PERSON (name required) or a MERCHANT (name ignored). */
public record CounterpartyRequest(Type type, String name) {

    public enum Type { PERSON, MERCHANT }
}
