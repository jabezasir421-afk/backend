package com.bluecollar.review.controller;

import com.bluecollar.common.dto.ApiResponse;
import com.bluecollar.review.dto.AdminReviewResponse;
import com.bluecollar.review.dto.ModerateReviewRequest;
import com.bluecollar.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin Reviews", description = "Admin review moderation and management")
public class AdminReviewController {

    private final ReviewService reviewService;

    @GetMapping("/pending")
    @Operation(
            summary = "Get pending reviews",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve reviews pending moderation approval.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<AdminReviewResponse>>> getPendingReviews(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getPendingReviews(pageable),
                "Pending reviews fetched successfully"
        ));
    }

    @PutMapping("/{id}/approve")
    @Operation(
            summary = "Approve a review",
            description = "Admin endpoint requiring JWT token with ADMIN role. Approve a review for public display.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
    @Operation(
            summary = "Reject a review",
            description = "Admin endpoint requiring JWT token with ADMIN role. Reject a review for moderation violation.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
    @Operation(
            summary = "Get all reviews",
            description = "Admin endpoint requiring JWT token with ADMIN role. Retrieve all reviews with moderation metadata.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<AdminReviewResponse>>> getAllReviews(
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getAllReviews(pageable),
                "Reviews fetched successfully"
        ));
    }
}
