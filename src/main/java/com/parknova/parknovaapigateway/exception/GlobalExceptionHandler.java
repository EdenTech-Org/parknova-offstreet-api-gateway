package com.parknova.parknovaapigateway.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        return body(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", HttpStatus.BAD_REQUEST.value());
        payload.put("error", "Validation failed");
        payload.put("fields", fields);
        return ResponseEntity.badRequest().body(payload);
    }

    @ExceptionHandler(jakarta.ws.rs.WebApplicationException.class)
    public ResponseEntity<Map<String, Object>> handleWebApplication(jakarta.ws.rs.WebApplicationException ex) {
        int remoteStatus = ex.getResponse() != null ? ex.getResponse().getStatus() : 0;
        if (remoteStatus == 403) {
            return body(HttpStatus.BAD_GATEWAY,
                    "Keycloak Admin API returned 403 Forbidden. "
                            + "Enable Service accounts on client 'parknova-api-gateway' and assign "
                            + "realm-management roles: manage-users, view-users, query-users, view-realm.");
        }
        return body(HttpStatus.BAD_GATEWAY,
                "Keycloak Admin API error: HTTP " + remoteStatus);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage() != null ? ex.getMessage() : "Unexpected error");
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        return ResponseEntity.status(status).body(payload);
    }
}
