package com.bluecollar.category.exception;

import java.util.UUID;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID id) {
        super("Category with id '%s' was not found".formatted(id));
    }
}
