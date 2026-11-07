package com.bluecollar.common.idempotency;

public record CachedHttpResponse(
        int statusCode,
        String contentType,
        byte[] body
) {
    public CachedHttpResponse {
        body = body != null ? body.clone() : new byte[0];
    }

    @Override
    public byte[] body() {
        return body != null ? body.clone() : new byte[0];
    }
}
