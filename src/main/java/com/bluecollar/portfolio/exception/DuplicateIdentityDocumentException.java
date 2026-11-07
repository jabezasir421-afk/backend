package com.bluecollar.portfolio.exception;

import com.bluecollar.portfolio.entity.DocumentType;

public class DuplicateIdentityDocumentException extends RuntimeException {

    public DuplicateIdentityDocumentException(DocumentType type) {
        super("Identity document of type " + type + " already exists");
    }

    public DuplicateIdentityDocumentException(String message) {
        super(message);
    }
}
