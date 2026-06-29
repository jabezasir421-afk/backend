package com.bluecollar.availability.exception;

public class WorkerNotAvailableException extends RuntimeException {

    public WorkerNotAvailableException(String message) {
        super(message);
    }
}
