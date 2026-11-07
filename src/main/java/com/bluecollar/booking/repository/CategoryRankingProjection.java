package com.bluecollar.booking.repository;

import com.bluecollar.category.entity.Category;

public interface CategoryRankingProjection {
    Category getCategory();
    long getBookingCount();
}
