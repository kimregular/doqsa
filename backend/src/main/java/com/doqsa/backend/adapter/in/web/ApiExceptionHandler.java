package com.doqsa.backend.adapter.in.web;

import com.doqsa.backend.application.service.OcrExceptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice @Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(OcrExceptions.NotFound.class)
    ResponseEntity<ProblemDetail> notFound(OcrExceptions.NotFound exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }
    @ExceptionHandler(OcrExceptions.Conflict.class)
    ResponseEntity<ProblemDetail> conflict(OcrExceptions.Conflict exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("API request failed", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be completed");
    }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
