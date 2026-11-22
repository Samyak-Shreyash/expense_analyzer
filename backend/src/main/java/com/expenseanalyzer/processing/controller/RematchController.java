package com.expenseanalyzer.processing.controller;

import com.expenseanalyzer.processing.service.RematchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lives in the processing module: it needs the enrichment, and processing already depends on transaction, so the
 * transaction module cannot host it without a cycle.
 */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/transactions")
public class RematchController {

    private final RematchService rematchService;

    @PostMapping("/rematch")
    public ResponseEntity<Void> rematch(@AuthenticationPrincipal Jwt jwt) {
        rematchService.rematch(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
