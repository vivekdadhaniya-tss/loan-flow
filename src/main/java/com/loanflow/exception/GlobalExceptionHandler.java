package com.loanflow.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1.) Handle custom application exceptions
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex,
                                                                    HttpServletRequest request) {

        log.error("Application exception: {}",  ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(ex.getStatus().value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .errorCode(ex.getErrorCode())
                .build();

        return ResponseEntity
                .status(ex.getStatus())
                .body(errorResponse);
    }

        // 2.) Handle validation exceptions for @RequestBody DTOs
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex,
                                                                        HttpServletRequest request) {
    
            log.error("Validation failed: {}",  ex.getMessage());
    
            String errorMessage = ex.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
    
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .status(ex.getStatusCode().value())
                    .message(errorMessage)
                    .path(request.getRequestURI())
                    .timestamp(Instant.now())
                    .errorCode("VALIDATION_ERROR")
                    .build();
    
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
    
        // 3.) Handle validation exceptions for @RequestParam / @PathVariable
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex,
                                                                                HttpServletRequest request) {
            log.error("Constraint violation: {}",  ex.getMessage());
    
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .message(ex.getMessage())
                    .path(request.getRequestURI())
                    .timestamp(Instant.now())
                    .errorCode("VALIDATION_ERROR")
                    .build();
    
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
    
        // 4.) Handle malformed JSON / Invalid enum / wrong request body types
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public  ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
                                                                                    HttpServletRequest request) {
            log.error("Malformed JSON request: {}",  ex.getMessage());
    
            ErrorResponse errorResponse = ErrorResponse.builder()
                    .status(HttpStatus.BAD_REQUEST.value())
                    .message("Invalid request body or malformed JSON")
                    .path(request.getRequestURI())
                    .timestamp(Instant.now())
                    .errorCode("INVALID_REQUEST_BODY")
                    .build();
    
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }

    // 5.) Handle access denied
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                     HttpServletRequest request) {
        log.error("Access denied: {}",  ex.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("You do not have permission to perform this action")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .errorCode("ACCESS_DENIED")
                .build();

        return  ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }

    // 6.) Handle all other uncaught exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {

        log.error("Unexpected exception occurred: {}",  ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Unexpected error occurred")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .errorCode("INTERNAL_SERVER_ERROR")
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}
