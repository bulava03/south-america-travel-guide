package com.project.guide.exception;

/**
 * Thrown when a sync is started while another one is running.
 */
public class SyncInProgressException extends RuntimeException {

    public SyncInProgressException() {
        super("A sync is already running");
    }
}
