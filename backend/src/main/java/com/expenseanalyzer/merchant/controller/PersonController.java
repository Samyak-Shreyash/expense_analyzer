package com.expenseanalyzer.merchant.controller;

import com.expenseanalyzer.category.dto.CategoryAssignment;
import com.expenseanalyzer.merchant.dto.PayeeMoveRequest;
import com.expenseanalyzer.merchant.dto.PersonMergeRequest;
import com.expenseanalyzer.merchant.dto.PersonRequest;
import com.expenseanalyzer.merchant.dto.PersonResponse;
import com.expenseanalyzer.merchant.service.PersonService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        Map<UUID, List<String>> payees = personService.payees(jwt.getSubject());
        return personService.list(jwt.getSubject()).stream()
                .map(p -> PersonResponse.from(p, payees.getOrDefault(p.getId(), List.of()))).toList();
    }

    @PutMapping("/{id}")
    public PersonResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                 @RequestBody PersonRequest body) {
        return PersonResponse.from(personService.update(jwt.getSubject(), id, body),
                personService.payees(jwt.getSubject()).getOrDefault(id, List.of()));
    }

    /** Sets the person's money-in or money-out default category. */
    @PutMapping("/{id}/category")
    public PersonResponse setCategory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @RequestBody CategoryAssignment body) {
        return PersonResponse.from(personService.setCategory(jwt.getSubject(), id, body.categoryId(),
                Boolean.TRUE.equals(body.credit())), personService.payees(jwt.getSubject()).getOrDefault(id, List.of()));
    }

    /** Merges this person into another; returns the caller's people. */
    @PostMapping("/{id}/merge")
    public List<PersonResponse> merge(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                      @RequestBody PersonMergeRequest body) {
        personService.merge(jwt.getSubject(), id, body == null ? null : body.intoId());
        return list(jwt);
    }

    /** Moves one of this person's payee names to another person, or to one of its own; returns the caller's people. */
    @PutMapping("/{id}/payees")
    public List<PersonResponse> movePayee(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @RequestBody PayeeMoveRequest body) {
        personService.movePayee(jwt.getSubject(), id, body == null ? null : body.payee(),
                body == null ? null : body.personId());
        return list(jwt);
    }
}
