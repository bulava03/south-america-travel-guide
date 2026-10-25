package com.project.guide.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a sync is started while another one is running.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SyncInProgressException extends RuntimeException {

    public SyncInProgressException() {
        super("A sync is already running");
    }
}
