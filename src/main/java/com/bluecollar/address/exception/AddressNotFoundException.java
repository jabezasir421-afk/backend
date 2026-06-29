package com.bluecollar.address.exception;

import java.util.UUID;

public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(UUID id) {
        super("Address with id '" + id + "' was not found");
    }
}
