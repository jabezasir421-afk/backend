package com.bluecollar.review.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.review.dto.CreateReviewRequest;
import com.bluecollar.review.dto.ReportReviewRequest;
import com.bluecollar.review.dto.ReviewResponse;
import com.bluecollar.review.dto.ReviewStatsResponse;
import com.bluecollar.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Worker review management")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Create review",
            description = "Customer creates a review for a completed booking.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request
    ) {
        ReviewResponse review = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(review, "Review created successfully"));
    }

    @GetMapping("/worker/{workerId}")
    @Operation(
            summary = "Get worker reviews",
            description = "Retrieve paginated list of approved reviews for a worker. Public endpoint, no authentication required.",
            security = {}
    )
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getWorkerReviews(
            @PathVariable UUID workerId,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getWorkerReviews(workerId, pageable), "Reviews fetched successfully"));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get review by ID",
            description = "Retrieve a specific review. Public endpoint, no authentication required.",
            security = {}
    )
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewById(id), "Review fetched successfully"));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Deactivate review",
            description = "Admin-only endpoint requiring JWT token with ADMIN role. Deactivate (hide) a review.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> deactivateReview(@PathVariable UUID id) {
        reviewService.deactivateReview(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Review deactivated successfully"));
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Activate review",
            description = "Admin-only endpoint requiring JWT token with ADMIN role. Activate (unhide) a review.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> activateReview(@PathVariable UUID id) {
        reviewService.activateReview(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Review activated successfully"));
    }

    @PostMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER')")
    @Operation(
            summary = "Report review",
            description = "Customer or worker reports a review for violation of terms. Requires JWT token with CUSTOMER or WORKER role.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> reportReview(
            @PathVariable UUID id,
            @Valid @RequestBody ReportReviewRequest request
    ) {
        reviewService.reportReview(id, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Review reported successfully"));
    }

    @GetMapping("/worker/{workerId}/stats")
    @Operation(
            summary = "Get worker review stats",
            description = "Retrieve review statistics (average rating, count) for a worker. Public endpoint, no authentication required.",
            security = {}
    )
    public ResponseEntity<ApiResponse<ReviewStatsResponse>> getWorkerReviewStats(@PathVariable UUID workerId) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getWorkerReviewStats(workerId),
                "Review statistics fetched successfully"
        ));
    }
}
