package com.expenseanalyzer.merchant.controller;

import com.expenseanalyzer.category.dto.CategoryAssignment;
import com.expenseanalyzer.category.service.CategoryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own per-merchant settings. Merchants are shared; the assignment is private to the caller. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final CategoryService categoryService;

    @PutMapping("/{merchantId}/category")
    public ResponseEntity<Void> setCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID merchantId,
                                            @RequestBody CategoryAssignment body) {
        categoryService.setMerchantCategory(jwt.getSubject(), merchantId, body.categoryId());
        return ResponseEntity.noContent().build();
    }
}
