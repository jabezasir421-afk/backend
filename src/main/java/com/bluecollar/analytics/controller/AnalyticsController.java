package com.bluecollar.analytics.controller;

import com.bluecollar.analytics.dto.*;
import com.bluecollar.analytics.service.AnalyticsService;
import com.bluecollar.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Analytics", description = "Admin platform analytics and statistics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(
            summary = "Get analytics overview",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve platform-wide analytics overview.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getOverview(), "Analytics overview fetched"));
    }

    @GetMapping("/bookings/daily")
    @Operation(
            summary = "Get daily booking stats",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve daily booking statistics for a date range.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
    @Operation(
            summary = "Get top categories",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve top service categories by booking count.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
    @Operation(
            summary = "Get top workers",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve top workers by booking count or earnings.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
    @Operation(
            summary = "Get rating distribution",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve worker rating distribution across the platform.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<RatingDistributionResponse>> getRatings() {
        return ResponseEntity.ok(ApiResponse.success(
                analyticsService.getRatingDistribution(),
                "Rating distribution fetched"
        ));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh analytics snapshot",
            description = "Admin endpoint requiring JWT token with ADMIN role. Manually refresh analytics cache for a specific date.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> refresh(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        analyticsService.refreshSnapshot(date == null ? LocalDate.now().minusDays(1) : date);
        return ResponseEntity.ok(ApiResponse.success(null, "Analytics snapshot refreshed"));
    }
}
