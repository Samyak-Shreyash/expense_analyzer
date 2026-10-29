package com.expenseanalyzer.merchant.controller;

import com.expenseanalyzer.merchant.dto.AccountSharingRequest;
import com.expenseanalyzer.merchant.dto.AliasSharingRequest;
import com.expenseanalyzer.merchant.dto.MerchantNameItem;
import com.expenseanalyzer.merchant.dto.MerchantNamesResponse;
import com.expenseanalyzer.merchant.dto.SharingSettingsResponse;
import com.expenseanalyzer.merchant.service.MerchantNameService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own merchant names and their sharing settings. Another user's name answers 404. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/merchant-names")
public class MerchantNameController {

    private final MerchantNameService merchantNameService;

    @GetMapping
    public MerchantNamesResponse list(@AuthenticationPrincipal Jwt jwt) {
        return merchantNameService.list(jwt.getSubject());
    }

    @GetMapping("/settings")
    public SharingSettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        return merchantNameService.settings(jwt.getSubject());
    }

    @PutMapping("/sharing")
    public MerchantNamesResponse setSharing(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountSharingRequest body) {
        return merchantNameService.setSharing(jwt.getSubject(), body.enabled());
    }

    @PutMapping("/{id}/sharing")
    public MerchantNameItem setAliasSharing(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                            @Valid @RequestBody AliasSharingRequest body) {
        return merchantNameService.setAliasSharing(jwt.getSubject(), id, body.shared());
    }
}
