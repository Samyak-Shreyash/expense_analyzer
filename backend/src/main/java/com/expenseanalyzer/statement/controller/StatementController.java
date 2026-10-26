package com.expenseanalyzer.statement.controller;

import com.expenseanalyzer.statement.dto.StatementResponse;
import com.expenseanalyzer.statement.dto.StatementStatusResponse;
import com.expenseanalyzer.statement.dto.StatementUploadResponse;
import com.expenseanalyzer.statement.service.StatementService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;

    /** POST /api/statements - multipart upload with a single part named "file". */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StatementUploadResponse> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(statementService.upload(
                jwt.getSubject(), file.getOriginalFilename(), file.getContentType(), file.getBytes()));
    }

    @GetMapping
    public List<StatementResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return statementService.list(jwt.getSubject());
    }

    @GetMapping("/{id}")
    public StatementResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return statementService.get(jwt.getSubject(), id);
    }

    @GetMapping("/{id}/status")
    public StatementStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return statementService.status(jwt.getSubject(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        statementService.delete(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
