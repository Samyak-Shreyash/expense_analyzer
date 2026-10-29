package com.expenseanalyzer.merchant.controller;

import com.expenseanalyzer.merchant.dto.PersonRequest;
import com.expenseanalyzer.merchant.dto.PersonResponse;
import com.expenseanalyzer.merchant.service.PersonService;
import java.util.List;
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

/** The caller's own people. Another user's person answers 404. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/people")
public class PersonController {

    private final PersonService personService;

    @GetMapping
    public List<PersonResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return personService.list(jwt.getSubject()).stream().map(PersonResponse::from).toList();
    }

    @PutMapping("/{id}")
    public PersonResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @RequestBody PersonRequest body) {
        return PersonResponse.from(personService.update(jwt.getSubject(), id, body));
    }
}
