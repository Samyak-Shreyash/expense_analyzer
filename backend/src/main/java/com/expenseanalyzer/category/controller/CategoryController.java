package com.expenseanalyzer.category.controller;

import com.expenseanalyzer.category.dto.CategoryRequest;
import com.expenseanalyzer.category.dto.CategoryResponse;
import com.expenseanalyzer.category.service.CategoryService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own spending categories. Another user's category answers 404, never 403. */
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return categoryService.list(jwt.getSubject()).stream().map(CategoryResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CategoryResponse.from(categoryService.create(jwt.getSubject(), request)));
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                   @RequestBody CategoryRequest request) {
        return CategoryResponse.from(categoryService.update(jwt.getSubject(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        categoryService.delete(jwt.getSubject(), id);
        return ResponseEntity.noContent().build();
    }
}
