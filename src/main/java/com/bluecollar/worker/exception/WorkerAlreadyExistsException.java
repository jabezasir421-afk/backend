package com.bluecollar.worker.exception;

public class WorkerAlreadyExistsException extends RuntimeException {

    public WorkerAlreadyExistsException(String fieldName, String value) {
        super("Worker with %s '%s' already exists".formatted(fieldName, value));
    }
}
