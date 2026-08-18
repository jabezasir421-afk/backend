package com.bluecollar.common;

import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.analytics.repository.AnalyticsCategoryRankingRepository;
import com.bluecollar.analytics.repository.AnalyticsDailySnapshotRepository;
import com.bluecollar.analytics.repository.AnalyticsWorkerRankingRepository;
import com.bluecollar.audit.repository.AuditLogRepository;
import com.bluecollar.auth.repository.RefreshTokenRepository;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.notification.repository.EmailOutboxRepository;
import com.bluecollar.notification.repository.NotificationPreferenceRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.review.repository.ReviewReportRepository;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.storage.repository.StoredFileRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestDbCleanup {

    private final BookingRepository bookingRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final ReviewRepository reviewRepository;
    private final EmailOutboxRepository emailOutboxRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final WorkerPortfolioItemRepository workerPortfolioItemRepository;
    private final WorkerCertificateRepository workerCertificateRepository;
    private final WorkerIdentityDocumentRepository workerIdentityDocumentRepository;
    private final StoredFileRepository storedFileRepository;
    private final AuditLogRepository auditLogRepository;
    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;
    private final WorkerRepository workerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AnalyticsCategoryRankingRepository analyticsCategoryRankingRepository;
    private final AnalyticsDailySnapshotRepository analyticsDailySnapshotRepository;
    private final AnalyticsWorkerRankingRepository analyticsWorkerRankingRepository;
    private final CategoryRepository categoryRepository;
    private final UserAccountRepository userAccountRepository;

    public void cleanCoreDomain() {
        reviewReportRepository.deleteAll();
        reviewRepository.deleteAll();
        bookingRepository.deleteAll();
        emailOutboxRepository.deleteAll();
        notificationRepository.deleteAll();
        notificationPreferenceRepository.deleteAll();
        workerPortfolioItemRepository.deleteAll();
        workerCertificateRepository.deleteAll();
        workerIdentityDocumentRepository.deleteAll();
        storedFileRepository.deleteAll();
        auditLogRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        analyticsCategoryRankingRepository.deleteAll();
        analyticsDailySnapshotRepository.deleteAll();
        analyticsWorkerRankingRepository.deleteAll();
        categoryRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    public void cleanAuthArtifacts() {
        refreshTokenRepository.deleteAll();
        customerRepository.deleteAll();
        workerRepository.deleteAll();
    }
}
