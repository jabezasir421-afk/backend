package com.bluecollar.common.idempotency;

public record CachedHttpResponse(
        int statusCode,
        String contentType,
        byte[] body
) {}
