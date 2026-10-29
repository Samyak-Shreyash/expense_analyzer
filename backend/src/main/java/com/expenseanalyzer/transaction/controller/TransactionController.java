package com.expenseanalyzer.transaction.controller;

import com.expenseanalyzer.category.dto.CategoryAssignment;
import com.expenseanalyzer.common.dto.PageResponse;
import com.expenseanalyzer.transaction.dto.CounterpartyRequest;
import com.expenseanalyzer.transaction.dto.MerchantNameRequest;
import com.expenseanalyzer.transaction.dto.TransactionFilter;
import com.expenseanalyzer.transaction.dto.TransactionResponse;
import com.expenseanalyzer.transaction.model.TransactionDirection;
import com.expenseanalyzer.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner-scoped transactions. Another user's transaction answers 404. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("createdAt"));

    private final TransactionService transactionService;

    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID statementId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID merchantId,
            @RequestParam(required = false) TransactionDirection direction,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TransactionFilter filter = new TransactionFilter(q, statementId, from, to, merchantId, direction, minAmount, maxAmount,
                category);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        return PageResponse.of(transactionService.list(jwt.getSubject(), filter, pageable), r -> r);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.of(transactionService.find(jwt.getSubject(), id));
    }

    @PutMapping("/{id}/category")
    public TransactionResponse setCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @RequestBody CategoryAssignment body) {
        return transactionService.setCategory(jwt.getSubject(), id, body.categoryId());
    }

    @PutMapping("/{id}/counterparty")
    public TransactionResponse setCounterparty(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                               @RequestBody CounterpartyRequest body) {
        return transactionService.setCounterparty(jwt.getSubject(), id, body);
    }

    @PutMapping("/{id}/merchant")
    public TransactionResponse setMerchant(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                           @RequestBody MerchantNameRequest body) {
        return transactionService.setMerchant(jwt.getSubject(), id, body.name(), body.share());
    }

    @PostMapping("/{id}/suggestion-report")
    public ResponseEntity<Void> reportSuggestion(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        transactionService.reportSuggestion(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
