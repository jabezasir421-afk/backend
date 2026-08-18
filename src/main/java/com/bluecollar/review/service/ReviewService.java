package com.bluecollar.review.service;

import com.bluecollar.review.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReviewService {

    ReviewResponse createReview(CreateReviewRequest request);

    Page<ReviewResponse> getWorkerReviews(UUID workerId, Pageable pageable);

    ReviewStatsResponse getWorkerReviewStats(UUID workerId);

    ReviewResponse getReviewById(UUID id);

    Page<AdminReviewResponse> getAllReviews(Pageable pageable);

    void deactivateReview(UUID id);

    void activateReview(UUID id);

    void reportReview(UUID reviewId, ReportReviewRequest request);

    Page<AdminReviewResponse> getPendingReviews(Pageable pageable);

    AdminReviewResponse approveReview(UUID id, ModerateReviewRequest request);

    AdminReviewResponse rejectReview(UUID id, ModerateReviewRequest request);
}
