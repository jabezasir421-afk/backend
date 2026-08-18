package com.bluecollar.customer.exception;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(UUID id) {
        super("Customer with id '" + id + "' was not found");
    }

    public CustomerNotFoundException(String message) {
        super(message);
    }
}
