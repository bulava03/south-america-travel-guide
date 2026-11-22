package com.project.guide.controller;

import com.project.guide.exception.AttractionNotFoundException;
import com.project.guide.exception.SyncInProgressException;
import com.project.guide.exception.UnknownCountryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converts exceptions of the REST API (public and admin) to RFC 7807 problem details.
 * Spring MVC errors (unreadable body, wrong parameter type) come from the base class, also as problem details.
 * Web pages are not affected: their errors are rendered by ErrorPageResolver.
 */
@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AttractionNotFoundException.class)
    public ProblemDetail handleNotFound(AttractionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SyncInProgressException.class)
    public ProblemDetail handleSyncInProgress(SyncInProgressException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnknownCountryException.class)
    public ProblemDetail handleUnknownCountry(UnknownCountryException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * The cause is only logged: its message may reveal internals.
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleError(RuntimeException e) {
        log.error("API request failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error, see the server log");
    }
}
