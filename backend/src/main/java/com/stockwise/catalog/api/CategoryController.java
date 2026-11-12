package com.stockwise.catalog.api;

import com.stockwise.catalog.application.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/categories")
@Profile("mysql")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody SaveCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                categoryService.create(businessId, userId(authentication), request));
    }

    @GetMapping
    public List<CategoryResponse> list(
            JwtAuthenticationToken authentication, @PathVariable UUID businessId) {
        return categoryService.list(businessId, userId(authentication));
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse get(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId) {
        return categoryService.get(businessId, userId(authentication), categoryId);
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody SaveCategoryRequest request) {
        return categoryService.update(
                businessId, userId(authentication), categoryId, request);
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId) {
        categoryService.delete(businessId, userId(authentication), categoryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{categoryId}/archive")
    public ResponseEntity<Void> archive(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID categoryId) {
        categoryService.archive(businessId, userId(authentication), categoryId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
