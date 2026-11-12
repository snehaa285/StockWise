package com.stockwise.shared.api;

import com.stockwise.auth.application.EmailAlreadyRegisteredException;
import com.stockwise.auth.application.AuthSessionNotFoundException;
import com.stockwise.auth.application.FirebaseAccountLinkRequiredException;
import com.stockwise.auth.application.FirebaseAuthenticationUnavailableException;
import com.stockwise.auth.application.FirebaseIdentityConflictException;
import com.stockwise.auth.application.FirebaseTokenVerificationException;
import com.stockwise.auth.application.InvalidCredentialsException;
import com.stockwise.auth.application.PasswordTooLongException;
import com.stockwise.business.application.BusinessConflictException;
import com.stockwise.business.application.BusinessForbiddenException;
import com.stockwise.business.application.BusinessMemberNotFoundException;
import com.stockwise.business.application.BusinessNotFoundException;
import com.stockwise.catalog.application.CatalogValidationException;
import com.stockwise.inventory.application.InventoryValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Validation failed");
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(PasswordTooLongException.class)
    ResponseEntity<ProblemDetail> handlePasswordTooLong(PasswordTooLongException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Validation failed");
        problem.setProperty(
                "fieldErrors",
                Map.of("password", "Password must be no more than 72 UTF-8 bytes."));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ProblemDetail> handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Email already registered");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, exception.getMessage());
        problem.setTitle("Authentication failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(FirebaseTokenVerificationException.class)
    ResponseEntity<ProblemDetail> handleInvalidFirebaseToken(
            FirebaseTokenVerificationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception.getMessage(), "Authentication failed");
    }

    @ExceptionHandler(FirebaseAuthenticationUnavailableException.class)
    ResponseEntity<ProblemDetail> handleFirebaseUnavailable(
            FirebaseAuthenticationUnavailableException exception) {
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage(),
                "Google sign-in unavailable");
    }

    @ExceptionHandler({FirebaseAccountLinkRequiredException.class, FirebaseIdentityConflictException.class})
    ResponseEntity<ProblemDetail> handleFirebaseIdentityConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "Account conflict");
    }

    @ExceptionHandler(AuthSessionNotFoundException.class)
    ResponseEntity<ProblemDetail> handleSessionNotFound(AuthSessionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "Session not found");
    }

    @ExceptionHandler(BusinessNotFoundException.class)
    ResponseEntity<ProblemDetail> handleBusinessNotFound(BusinessNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "Business not found");
    }

    @ExceptionHandler(BusinessMemberNotFoundException.class)
    ResponseEntity<ProblemDetail> handleMemberNotFound(
            BusinessMemberNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "Business member not found");
    }

    @ExceptionHandler(BusinessForbiddenException.class)
    ResponseEntity<ProblemDetail> handleBusinessForbidden(BusinessForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "Access denied");
    }

    @ExceptionHandler(BusinessConflictException.class)
    ResponseEntity<ProblemDetail> handleBusinessConflict(BusinessConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "Business conflict");
    }

    @ExceptionHandler(CatalogValidationException.class)
    ResponseEntity<ProblemDetail> handleCatalogValidation(
            CatalogValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Catalog validation failed.");
        problem.setTitle("Validation failed");
        problem.setProperty("fieldErrors", Map.of(exception.getField(), exception.getMessage()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InventoryValidationException.class)
    ResponseEntity<ProblemDetail> handleInventoryValidation(
            InventoryValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Inventory validation failed.");
        problem.setTitle("Validation failed");
        problem.setProperty("fieldErrors", Map.of(exception.getField(), exception.getMessage()));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataConflict(DataIntegrityViolationException exception) {
        return problem(
                HttpStatus.CONFLICT,
                "The requested change conflicts with existing data.",
                "Resource conflict");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = exception.getReason() == null
                ? "The request could not be completed."
                : exception.getReason();
        return problem(status, detail, "Request failed");
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String detail, String title) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).body(problem);
    }
}
