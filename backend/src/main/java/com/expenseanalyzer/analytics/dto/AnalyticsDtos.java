package com.expenseanalyzer.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Analytics responses. Amounts are in currency units (rupees, scale 2). Percentages have scale 2 and are
 * null when the comparison base is zero.
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record PeriodTotals(LocalDate from, LocalDate to, BigDecimal inflow, BigDecimal outflow, BigDecimal net,
                               long transactionCount, BigDecimal nonSpendingOutflow) {
    }

    public record SpendingSummary(LocalDate from, LocalDate to, String currency, BigDecimal inflow,
                                  BigDecimal outflow, BigDecimal net, long transactionCount, PeriodTotals previous,
                                  BigDecimal inflowChangePercent, BigDecimal outflowChangePercent,
                                  BigDecimal nonSpendingOutflow, BigDecimal transfersIn, BigDecimal transfersOut) {
    }

    public record MerchantSpend(UUID merchantId, String name, BigDecimal spend, BigDecimal sharePercent,
                                long transactionCount) {
    }

    public record MerchantBreakdown(LocalDate from, LocalDate to, String currency, BigDecimal totalSpend,
                                    List<MerchantSpend> items) {
    }

    public record CategorySpend(String category, BigDecimal spend, BigDecimal sharePercent, long transactionCount) {
    }

    public record CategoryBreakdown(LocalDate from, LocalDate to, String currency, BigDecimal totalSpend,
                                    List<CategorySpend> items) {
    }

    public record MonthlyCashFlow(String month, BigDecimal inflow, BigDecimal outflow, BigDecimal net) {
    }

    public record CashFlow(LocalDate from, LocalDate to, String currency, List<MonthlyCashFlow> months) {
    }

    public record MonthlyTrend(String month, BigDecimal outflow, BigDecimal changePercent) {
    }

    public record Trends(LocalDate from, LocalDate to, String currency, List<MonthlyTrend> months) {
    }

    public record PersonSpend(UUID personId, String name, BigDecimal spend, long transactionCount) {
    }

    public record NonSpendingCategory(String category, BigDecimal spend, BigDecimal sharePercent,
                                      long transactionCount, List<PersonSpend> people) {
    }

    public record NonSpendingBreakdown(LocalDate from, LocalDate to, String currency, BigDecimal total,
                                       List<NonSpendingCategory> items) {
    }
}
