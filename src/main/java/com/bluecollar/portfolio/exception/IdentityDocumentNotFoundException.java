package com.bluecollar.portfolio.exception;

import java.util.UUID;

public class IdentityDocumentNotFoundException extends RuntimeException {

    public IdentityDocumentNotFoundException(UUID id) {
        super("Identity document not found with id: " + id);
    }
}
