package com.bluecollar.review.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.review.dto.CreateReviewRequest;
import com.bluecollar.review.dto.ReportReviewRequest;
import com.bluecollar.review.dto.ReviewResponse;
import com.bluecollar.review.dto.ReviewStatsResponse;
import com.bluecollar.review.service.ReviewService;
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
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Valid @RequestBody CreateReviewRequest request
    ) {
        ReviewResponse review = reviewService.createReview(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(review, "Review created successfully"));
    }

    @GetMapping("/worker/{workerId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getWorkerReviews(
            @PathVariable UUID workerId,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getWorkerReviews(workerId, pageable), "Reviews fetched successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReviewResponse>> getReviewById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewById(id), "Review fetched successfully"));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateReview(@PathVariable UUID id) {
        reviewService.deactivateReview(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Review deactivated successfully"));
    }

    @PostMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER')")
    public ResponseEntity<ApiResponse<Void>> reportReview(
            @PathVariable UUID id,
            @Valid @RequestBody ReportReviewRequest request
    ) {
        reviewService.reportReview(id, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Review reported successfully"));
    }

    @GetMapping("/worker/{workerId}/stats")
    public ResponseEntity<ApiResponse<ReviewStatsResponse>> getWorkerReviewStats(@PathVariable UUID workerId) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getWorkerReviewStats(workerId),
                "Review statistics fetched successfully"
        ));
    }
}
