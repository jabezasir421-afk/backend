package com.bluecollar.review.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.review.dto.AdminReviewResponse;
import com.bluecollar.review.dto.ModerateReviewRequest;
import com.bluecollar.review.dto.ReviewResponse;
import com.bluecollar.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<AdminReviewResponse>>> getPendingReviews(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getPendingReviews(pageable),
                "Pending reviews fetched successfully"
        ));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> approveReview(
            @PathVariable UUID id,
            @Valid @RequestBody ModerateReviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.approveReview(id, request),
                "Review approved successfully"
        ));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> rejectReview(
            @PathVariable UUID id,
            @Valid @RequestBody ModerateReviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.rejectReview(id, request),
                "Review rejected successfully"
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getAllReviews(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getAllReviews(pageable),
                "Reviews fetched successfully"
        ));
    }
}
