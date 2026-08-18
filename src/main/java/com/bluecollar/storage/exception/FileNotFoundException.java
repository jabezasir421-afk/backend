package com.bluecollar.storage.exception;

import java.util.UUID;

public class FileNotFoundException extends RuntimeException {

    public FileNotFoundException(UUID id) {
        super("File not found with id: " + id);
    }
}
