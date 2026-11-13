package queueforge.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(JobNotFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({IllegalStateException.class, IdempotencyConflictException.class})
    public ResponseEntity<Map<String, Object>> conflict(RuntimeException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "conflict", ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "bad_request", "Request is invalid", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage(), (a, b) -> a, LinkedHashMap::new));
        Map<String, Object> body = base(HttpStatus.BAD_REQUEST, "validation_failed", request.getRequestURI());
        body.put("fieldErrors", fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> rateLimited(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(429).header("Retry-After", "1")
                .body(body(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RateLimiterUnavailableException.class)
    public ResponseEntity<Map<String, Object>> rateLimiterUnavailable(RateLimiterUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body(HttpStatus.SERVICE_UNAVAILABLE, "rate_limiter_unavailable", "Rate limiter is unavailable", request.getRequestURI()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> concurrentUpdate(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "concurrent_update", "The resource changed concurrently; reload and retry", request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataConflict(DataIntegrityViolationException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "data_conflict", "Request conflicts with an existing record", request.getRequestURI());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> databaseUnavailable(DataAccessException ex, HttpServletRequest request) {
        log.error("Database operation failed at {}", request.getRequestURI(), ex);
        return response(HttpStatus.SERVICE_UNAVAILABLE, "database_unavailable", "Database operation is temporarily unavailable", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request error at {}", request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred", request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String error, String message, String path) {
        return ResponseEntity.status(status).body(body(status, error, message, path));
    }
    private Map<String, Object> body(HttpStatus status, String error, String message, String path) {
        Map<String, Object> body = base(status, error, path);
        body.put("message", message);
        return body;
    }
    private Map<String, Object> base(HttpStatus status, String error, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("path", path);
        return body;
    }
}
