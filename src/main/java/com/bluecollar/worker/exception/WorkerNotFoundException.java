package com.bluecollar.worker.exception;

import java.util.UUID;

public class WorkerNotFoundException extends RuntimeException {

    public WorkerNotFoundException(UUID id) {
        super("Worker with id '%s' was not found".formatted(id));
    }

    public WorkerNotFoundException(String message) {
        super(message);
    }
}
