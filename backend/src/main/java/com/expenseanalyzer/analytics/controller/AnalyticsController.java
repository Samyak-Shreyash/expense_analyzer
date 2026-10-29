package com.expenseanalyzer.analytics.controller;

import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CashFlow;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CategoryBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MerchantBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.NonSpendingBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.SpendingSummary;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.Trends;
import com.expenseanalyzer.analytics.service.SpendingAnalyticsService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Spending analytics for the caller's own transactions. {@code from}/{@code to} are inclusive ISO dates. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final int MAX_MERCHANTS = 50;

    private final SpendingAnalyticsService analytics;

    @GetMapping("/summary")
    public SpendingSummary summary(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.summary(jwt.getSubject(), from, to);
    }

    @GetMapping("/merchants")
    public MerchantBreakdown merchants(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                       @RequestParam(defaultValue = "10") int limit) {
        return analytics.merchants(jwt.getSubject(), from, to, Math.clamp(limit, 1, MAX_MERCHANTS));
    }

    @GetMapping("/categories")
    public CategoryBreakdown categories(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.categories(jwt.getSubject(), from, to);
    }

    @GetMapping("/cash-flow")
    public CashFlow cashFlow(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.cashFlow(jwt.getSubject(), from, to);
    }

    @GetMapping("/trends")
    public Trends trends(@AuthenticationPrincipal Jwt jwt,
                         @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.trends(jwt.getSubject(), from, to);
    }

    @GetMapping("/non-spending")
    public NonSpendingBreakdown nonSpending(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return analytics.nonSpending(jwt.getSubject(), from, to);
    }
}
