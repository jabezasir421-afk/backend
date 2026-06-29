package com.bluecollar.analytics.dto;

import java.time.LocalDate;

public record DailyBookingStatsResponse(LocalDate date, int created, int completed, int cancelled) {
}
