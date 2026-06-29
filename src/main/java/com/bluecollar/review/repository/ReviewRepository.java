package com.bluecollar.review.repository;

import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
