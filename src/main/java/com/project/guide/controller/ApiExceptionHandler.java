package com.project.guide.controller;

import com.project.guide.exception.AttractionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts REST API exceptions to RFC 7807 problem details.
 */
@RestControllerAdvice(assignableTypes = AttractionApiController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(AttractionNotFoundException.class)
    public ProblemDetail handleNotFound(AttractionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
