package com.igorpujovic.finance_tracker.common;

import com.igorpujovic.finance_tracker.rates.RatesUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", e.getMessage()));
    }

    /**
     * An upload past the size the container will buffer at all. It is the same mistake
     * as a file past the limit the importer enforces, so it is reported the same way
     * rather than as the 413 Spring would otherwise produce.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "The uploaded file is too large"));
    }

    /**
     * ADR-0005: a failure to reach the National Bank is not the statement's fault, and
     * recording it as one would write a permanent audit record accusing valid rows of
     * being malformed because the network blinked. The pre-pass runs before the import
     * transaction opens, so aborting here costs nothing and compensates for nothing —
     * there is no partial write to undo and no half-finished batch left in the history.
     */
    @ExceptionHandler(RatesUnavailableException.class)
    public ResponseEntity<ApiError> ratesUnavailable(RatesUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "Service Unavailable", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(error -> fields.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", "Validation failed", fields));
    }

    /**
     * A constraint the database enforces and the application did not catch first.
     * It is reported rather than swallowed: every one of these is a missing check
     * further up, and a 409 with the constraint name says which.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        String detail = e.getMostSpecificCause().getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "Conflict", detail));
    }
}
