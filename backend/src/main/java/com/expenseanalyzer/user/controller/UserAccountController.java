package com.expenseanalyzer.user.controller;

import com.expenseanalyzer.transaction.service.TransferService;
import com.expenseanalyzer.user.dto.UserAccountRequest;
import com.expenseanalyzer.user.dto.UserAccountResponse;
import com.expenseanalyzer.user.model.UserAccount;
import com.expenseanalyzer.user.service.UserAccountService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The user's own account numbers; adding one re-checks transfers. Another user's account answers 404. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/accounts")
public class UserAccountController {

    private final UserAccountService accountService;
    private final TransferService transferService;

    @GetMapping
    public List<UserAccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(jwt.getSubject()).stream().map(UserAccountResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<UserAccountResponse> add(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody UserAccountRequest request) {
        UserAccount a = accountService.add(jwt.getSubject(), request.accountNumber(), request.bankName());
        transferService.match(a.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserAccountResponse.from(a));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return accountService.delete(jwt.getSubject(), id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
