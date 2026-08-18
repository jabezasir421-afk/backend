package com.bluecollar.analytics.controller;

import com.bluecollar.analytics.dto.*;
import com.bluecollar.analytics.service.AnalyticsService;
import com.bluecollar.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getOverview(), "Analytics overview fetched"));
    }

    @GetMapping("/bookings/daily")
    public ResponseEntity<ApiResponse<List<DailyBookingStatsResponse>>> getDailyBookings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getDailyBookings(from, to),
                "Daily booking stats fetched"
        ));
    }

    @GetMapping("/categories/top")
    public ResponseEntity<ApiResponse<List<TopCategoryResponse>>> getTopCategories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getTopCategories(from, to, limit),
                "Top categories fetched"
        ));
    }

    @GetMapping("/workers/top")
    public ResponseEntity<ApiResponse<List<TopWorkerResponse>>> getTopWorkers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getTopWorkers(from, to, limit),
                "Top workers fetched"
        ));
    }

    @GetMapping("/ratings")
    public ResponseEntity<ApiResponse<RatingDistributionResponse>> getRatings() {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getRatingDistribution(),
                "Rating distribution fetched"
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Void>> refresh(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        analyticsService.refreshSnapshot(date == null ? LocalDate.now().minusDays(1) : date);
        return ResponseEntity.ok(ApiResponse.success(null, "Analytics snapshot refreshed"));
    }
}
