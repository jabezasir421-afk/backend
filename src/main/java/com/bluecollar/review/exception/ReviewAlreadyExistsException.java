package com.bluecollar.review.exception;

import java.util.UUID;

public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException(UUID bookingId) {
        super("Review already exists for booking '" + bookingId + "'");
    }
}
