package com.stockwise.catalog.api;

import com.stockwise.catalog.application.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/products")
@Profile("mysql")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @Valid @RequestBody SaveProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                productService.create(businessId, userId(authentication), request));
    }

    @GetMapping
    public List<ProductResponse> list(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @RequestParam(required = false) UUID categoryId) {
        return productService.list(businessId, userId(authentication), categoryId);
    }

    @GetMapping("/{productId}")
    public ProductResponse get(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID productId) {
        return productService.get(businessId, userId(authentication), productId);
    }

    @PutMapping("/{productId}")
    public ProductResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID productId,
            @Valid @RequestBody SaveProductRequest request) {
        return productService.update(
                businessId, userId(authentication), productId, request);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> archive(
            JwtAuthenticationToken authentication,
            @PathVariable UUID businessId,
            @PathVariable UUID productId) {
        productService.archive(businessId, userId(authentication), productId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(JwtAuthenticationToken authentication) {
        return UUID.fromString(authentication.getName());
    }
}
