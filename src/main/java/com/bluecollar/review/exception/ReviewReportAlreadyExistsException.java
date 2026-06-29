package com.bluecollar.review.exception;

import java.util.UUID;

public class ReviewReportAlreadyExistsException extends RuntimeException {

    public ReviewReportAlreadyExistsException(UUID reviewId) {
        super("An open report already exists for review: " + reviewId);
    }
}
