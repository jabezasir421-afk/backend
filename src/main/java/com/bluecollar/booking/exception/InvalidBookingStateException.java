package com.bluecollar.booking.exception;

import com.bluecollar.booking.entity.BookingStatus;

public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(BookingStatus current, BookingStatus target) {
        super("Cannot transition booking from " + current + " to " + target);
    }

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
