package com.bluecollar.analytics.dto;

import java.util.UUID;

public record TopCategoryResponse(UUID categoryId, String categoryName, long bookingCount, int rank) {
}
