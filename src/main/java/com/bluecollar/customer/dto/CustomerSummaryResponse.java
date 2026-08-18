package com.bluecollar.customer.dto;

import java.util.UUID;

public record CustomerSummaryResponse(
        UUID id,
        String firstName,
        String lastName
) {
}
