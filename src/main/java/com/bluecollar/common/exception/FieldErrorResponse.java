package com.bluecollar.common.exception;

public record FieldErrorResponse(
        String field,
        String message
) {
}
