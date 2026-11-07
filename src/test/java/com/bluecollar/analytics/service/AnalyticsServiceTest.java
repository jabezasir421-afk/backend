package com.bluecollar.analytics.service;

import com.bluecollar.analytics.dto.AnalyticsOverviewResponse;
import com.bluecollar.analytics.dto.RatingDistributionResponse;
import com.bluecollar.analytics.dto.TopCategoryResponse;
import com.bluecollar.analytics.dto.TopWorkerResponse;
import com.bluecollar.analytics.entity.AnalyticsDailySnapshot;
import com.bluecollar.analytics.repository.AnalyticsCategoryRankingRepository;
import com.bluecollar.analytics.repository.AnalyticsDailySnapshotRepository;
import com.bluecollar.analytics.repository.AnalyticsWorkerRankingRepository;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.booking.repository.CategoryBookingStatsProjection;
import com.bluecollar.booking.repository.WorkerBookingStatsProjection;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AnalyticsDailySnapshotRepository snapshotRepository;

    @Mock
    private AnalyticsCategoryRankingRepository categoryRankingRepository;

    @Mock
    private AnalyticsWorkerRankingRepository workerRankingRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @Test
    void getOverviewShouldAggregatePlatformMetrics() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        when(workerRepository.count()).thenReturn(10L);
        when(workerRepository.countByActiveTrue()).thenReturn(8L);
        when(customerRepository.count()).thenReturn(20L);
        when(customerRepository.countByActiveTrue()).thenReturn(18L);
        when(bookingRepository.count()).thenReturn(50L);
        when(bookingRepository.countByCreatedAtBetween(
                today.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        )).thenReturn(3L);
        when(reviewRepository.findAverageRatingForActiveApprovedReviews()).thenReturn(5.0);
        when(bookingRepository.sumFinalAmountByStatus(BookingStatus.COMPLETED)).thenReturn(new BigDecimal("1500.50"));

        AnalyticsOverviewResponse result = analyticsService.getOverview();

        assertEquals(10L, result.totalWorkers());
        assertEquals(8L, result.activeWorkers());
        assertEquals(20L, result.totalCustomers());
        assertEquals(18L, result.activeCustomers());
        assertEquals(50L, result.totalBookings());
        assertEquals(3L, result.bookingsToday());
        assertEquals(new BigDecimal("5.00"), result.averagePlatformRating());
        assertEquals(new BigDecimal("1500.50"), result.totalRevenue());
    }

    @Test
    void getRatingDistributionShouldReturnCountsAndAverage() {
        when(reviewRepository.countDistributionForActiveApprovedReviews()).thenReturn(List.of(
                new Object[]{(short) 5, 1L},
                new Object[]{(short) 4, 1L}
        ));
        when(reviewRepository.findAverageRatingForActiveApprovedReviews()).thenReturn(4.5);

        RatingDistributionResponse result = analyticsService.getRatingDistribution();

        assertEquals(2L, result.totalReviews());
        assertEquals(new BigDecimal("4.50"), result.average());
        assertEquals(1L, result.distribution().get((short) 5));
        assertEquals(1L, result.distribution().get((short) 4));
        assertEquals(0L, result.distribution().get((short) 1));
    }

    @Test
    void refreshSnapshotShouldPersistDailySnapshotAndRankings() {
        LocalDate date = LocalDate.of(2026, 6, 27);
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        when(snapshotRepository.findBySnapshotDate(date)).thenReturn(Optional.empty());
        when(workerRepository.count()).thenReturn(5L);
        when(workerRepository.countByActiveTrue()).thenReturn(4L);
        when(customerRepository.count()).thenReturn(12L);
        when(customerRepository.countByActiveTrue()).thenReturn(10L);
        when(bookingRepository.countByCreatedAtBetween(start, end)).thenReturn(4L);
        when(bookingRepository.countByStatusAndCompletedAtBetween(BookingStatus.COMPLETED, start, end)).thenReturn(2L);
        when(bookingRepository.countByStatusAndCancelledAtBetween(BookingStatus.CANCELLED, start, end)).thenReturn(1L);
        when(bookingRepository.sumFinalAmountByStatusAndCompletedAtBetween(BookingStatus.COMPLETED, start, end))
                .thenReturn(new BigDecimal("250.00"));
        when(reviewRepository.findAverageRatingForActiveApprovedReviewsCreatedBetween(start, end)).thenReturn(5.0);
        when(bookingRepository.findTopCategoriesForRanking(eq(BookingStatus.COMPLETED), any(Pageable.class))).thenReturn(List.of());
        when(bookingRepository.findTopWorkersForRanking(eq(BookingStatus.COMPLETED), any(Pageable.class))).thenReturn(List.of());
        when(categoryRepository.findByActiveTrue()).thenReturn(List.of());
        when(workerRepository.findByActiveTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        analyticsService.refreshSnapshot(date);

        verify(snapshotRepository).save(any(AnalyticsDailySnapshot.class));
        verify(categoryRankingRepository).deleteBySnapshotDate(date);
        verify(categoryRankingRepository).saveAll(eq(List.of()));
        verify(workerRankingRepository).deleteBySnapshotDate(date);
        verify(workerRankingRepository).saveAll(eq(List.of()));
    }

    @Test
    void getTopCategoriesWithDateRangeShouldQueryBookingRepository() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        UUID catId = UUID.randomUUID();

        CategoryBookingStatsProjection projection = new CategoryBookingStatsProjection() {
            @Override
            public UUID getCategoryId() {
                return catId;
            }

            @Override
            public String getCategoryName() {
                return "Plumbing";
            }

            @Override
            public long getBookingCount() {
                return 15L;
            }
        };

        when(bookingRepository.findTopCategoriesWithDateRange(eq(from), eq(to), any(Pageable.class)))
                .thenReturn(List.of(projection));

        List<TopCategoryResponse> result = analyticsService.getTopCategories(from, to, 10);

        assertEquals(1, result.size());
        assertEquals(catId, result.getFirst().categoryId());
        assertEquals("Plumbing", result.getFirst().categoryName());
        assertEquals(15, result.getFirst().bookingCount());
        assertEquals((short) 1, result.getFirst().rank());
    }

    @Test
    void getTopWorkersWithDateRangeShouldQueryBookingRepository() {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        UUID workerId = UUID.randomUUID();

        WorkerBookingStatsProjection projection = new WorkerBookingStatsProjection() {
            @Override
            public UUID getWorkerId() {
                return workerId;
            }

            @Override
            public String getFirstName() {
                return "Bob";
            }

            @Override
            public String getLastName() {
                return "Builder";
            }

            @Override
            public long getCompletedBookings() {
                return 22L;
            }
        };

        when(bookingRepository.findTopWorkersWithDateRange(eq(from), eq(to), any(Pageable.class)))
                .thenReturn(List.of(projection));

        List<TopWorkerResponse> result = analyticsService.getTopWorkers(from, to, 10);

        assertEquals(1, result.size());
        assertEquals(workerId, result.getFirst().workerId());
        assertEquals("Bob Builder", result.getFirst().fullName());
        assertEquals(22, result.getFirst().completedBookings());
        assertEquals((short) 1, result.getFirst().rank());
    }
}
