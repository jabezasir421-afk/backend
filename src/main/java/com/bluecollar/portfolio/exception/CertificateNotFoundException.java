package com.bluecollar.portfolio.exception;

import java.util.UUID;

public class CertificateNotFoundException extends RuntimeException {

    public CertificateNotFoundException(UUID id) {
        super("Certificate not found with id: " + id);
    }

    public CertificateNotFoundException(String message) {
        super(message);
    }
}
