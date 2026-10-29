package com.expenseanalyzer.statement.controller;

import com.expenseanalyzer.common.dto.PageResponse;
import com.expenseanalyzer.statement.dto.StatementResponse;
import com.expenseanalyzer.statement.dto.StatementStatusResponse;
import com.expenseanalyzer.statement.service.StatementService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Statement upload and owner-scoped metadata. Another user's statement answers 404, never 403. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private static final int MAX_PAGE_SIZE = 100;

    private final StatementService statementService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StatementResponse.from(statementService.upload(jwt.getSubject(), file)));
    }

    @GetMapping
    public PageResponse<StatementResponse> list(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.of(statementService.list(jwt.getSubject(), pageable), StatementResponse::from);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StatementResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.of(statementService.view(jwt.getSubject(), id).map(StatementResponse::from));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<StatementStatusResponse> status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.of(statementService.view(jwt.getSubject(), id).map(StatementStatusResponse::from));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return statementService.delete(jwt.getSubject(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
