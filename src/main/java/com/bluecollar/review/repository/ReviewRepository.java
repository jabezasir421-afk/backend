package com.bluecollar.review.repository;

import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingId(UUID bookingId);

    Page<Review> findByWorkerIdAndActiveTrueAndModerationStatus(
            UUID workerId, ModerationStatus moderationStatus, Pageable pageable
    );

    List<Review> findByWorkerIdAndActiveTrueAndModerationStatus(UUID workerId, ModerationStatus moderationStatus);

    Page<Review> findByModerationStatus(ModerationStatus moderationStatus, Pageable pageable);

    Optional<Review> findByIdAndActiveTrue(UUID id);

    @Query("""
            SELECT AVG(r.rating) FROM Review r
            WHERE r.active = true AND r.moderationStatus = com.bluecollar.review.entity.ModerationStatus.APPROVED
            """)
    Double findAverageRatingForActiveApprovedReviews();

    @Query("""
            SELECT r.rating, COUNT(r) FROM Review r
            WHERE r.active = true AND r.moderationStatus = com.bluecollar.review.entity.ModerationStatus.APPROVED
            GROUP BY r.rating
            """)
    List<Object[]> countDistributionForActiveApprovedReviews();

    long countByActiveTrueAndModerationStatus(ModerationStatus moderationStatus);

    @Query("""
            SELECT AVG(r.rating) FROM Review r
            WHERE r.active = true AND r.moderationStatus = com.bluecollar.review.entity.ModerationStatus.APPROVED
              AND r.createdAt >= :start AND r.createdAt < :end
            """)
    Double findAverageRatingForActiveApprovedReviewsCreatedBetween(
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
