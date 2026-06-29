package com.bluecollar.review.repository;

import com.bluecollar.review.entity.ReportStatus;
import com.bluecollar.review.entity.ReviewReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, UUID> {

    boolean existsByReviewIdAndReporterUserIdAndStatus(UUID reviewId, UUID reporterUserId, ReportStatus status);

    long countByReviewIdAndStatus(UUID reviewId, ReportStatus status);

    Page<ReviewReport> findByStatus(ReportStatus status, Pageable pageable);
}
