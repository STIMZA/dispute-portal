package com.example.disputeportal.controller;

import com.example.disputeportal.dto.ApiError;
import com.example.disputeportal.dto.ApiProblem;
import com.example.disputeportal.exception.BatchRejectedException;
import com.example.disputeportal.security.PayloadTooLargeException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns API failures into the standard JSON error body. Limited to the two API controllers so it never
 * affects the HTML pages. Messages are fixed text: nothing from the request body is echoed back.
 */
@RestControllerAdvice(assignableTypes = {TransactionLoadController.class, CustomerProvisionController.class})
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Bean Validation failed on the request body: 422, with one problem per invalid field.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiProblem> problems = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiProblem(e.getField(), e.getDefaultMessage()))
                .toList();
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", problems);
    }

    /**
     * The load service rejected the whole batch (422 for invalid items, 409 for conflicts). Nothing was saved.
     */
    @ExceptionHandler(BatchRejectedException.class)
    public ResponseEntity<ApiError> handleLoad(BatchRejectedException ex) {
        return body(ex.getStatus(), ex.getMessage(), ex.getProblems());
    }

    /**
     * The body couldn't be read: malformed JSON (400), or it exceeded the size limit while being read (413, signalled by
     * the PayloadTooLargeException thrown from LimitedBodyRequest).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof PayloadTooLargeException) {
                return body(HttpStatus.PAYLOAD_TOO_LARGE, "Request body is too large", List.of());
            }
        }
        return body(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON", List.of());
    }

    /**
     * The database refused the insert, typically a unique-reference clash with a concurrent load.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        // Most likely two loads of the same reference at once. Nothing was saved, so retrying is safe.
        log.warn("Transaction load hit a data integrity violation");
        return body(HttpStatus.CONFLICT, "The batch conflicted with a concurrent change; retry it", List.of());
    }

    /**
     * Builds the standard JSON error response.
     */
    private static ResponseEntity<ApiError> body(HttpStatus status, String title, List<ApiProblem> problems) {
        return ResponseEntity.status(status).body(new ApiError(title, status.value(), problems));
    }
}
