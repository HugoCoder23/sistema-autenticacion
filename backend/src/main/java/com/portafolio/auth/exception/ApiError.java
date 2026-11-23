package com.portafolio.auth.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/** Formato único para todas las respuestas de error de la API. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}

    public static ApiError of(HttpStatus status, String code, String message, String path) {
        return of(status, code, message, path, null);
    }

    public static ApiError of(HttpStatus status, String code, String message, String path, List<FieldError> fields) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code, message, path,
                fields == null || fields.isEmpty() ? null : fields);
    }
}
