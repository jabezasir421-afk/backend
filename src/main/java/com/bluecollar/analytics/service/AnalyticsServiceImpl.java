package com.bluecollar.analytics.service;

import com.bluecollar.analytics.dto.*;
import com.bluecollar.analytics.entity.AnalyticsCategoryRanking;
import com.bluecollar.analytics.entity.AnalyticsDailySnapshot;
import com.bluecollar.analytics.entity.AnalyticsWorkerRanking;
import com.bluecollar.analytics.repository.AnalyticsCategoryRankingRepository;
import com.bluecollar.analytics.repository.AnalyticsDailySnapshotRepository;
import com.bluecollar.analytics.repository.AnalyticsWorkerRankingRepository;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.review.entity.ModerationStatus;
import com.bluecollar.review.entity.Review;
import com.bluecollar.review.repository.ReviewRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final WorkerRepository workerRepository;
    private final CustomerRepository customerRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final CategoryRepository categoryRepository;
    private final AnalyticsDailySnapshotRepository snapshotRepository;
    private final AnalyticsCategoryRankingRepository categoryRankingRepository;
    private final AnalyticsWorkerRankingRepository workerRankingRepository;

    @Override
    public AnalyticsOverviewResponse getOverview() {
        long totalWorkers = workerRepository.count();
        long activeWorkers = workerRepository.countByActiveTrue();
        long totalCustomers = customerRepository.count();
        long activeCustomers = customerRepository.countByActiveTrue();
        long totalBookings = bookingRepository.count();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long bookingsToday = bookingRepository.countByCreatedAtBetween(
                today.atStartOfDay().toInstant(ZoneOffset.UTC),
                today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        );
        List<Review> reviews = reviewRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()) && r.getModerationStatus() == ModerationStatus.APPROVED)
                .toList();
        BigDecimal avgRating = reviews.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(reviews.stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal revenue = bookingRepository.sumFinalAmountByStatus(BookingStatus.COMPLETED);
        return new AnalyticsOverviewResponse(
                totalWorkers, activeWorkers, totalCustomers, activeCustomers,
                totalBookings, bookingsToday, avgRating, revenue == null ? BigDecimal.ZERO : revenue
        );
    }

    @Override
    public List<DailyBookingStatsResponse> getDailyBookings(LocalDate from, LocalDate to) {
        return snapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDateAsc(from, to).stream()
                .map(s -> new DailyBookingStatsResponse(
                        s.getSnapshotDate(), s.getBookingsCreated(),
                        s.getBookingsCompleted(), s.getBookingsCancelled()))
                .toList();
    }

    @Override
    public List<TopCategoryResponse> getTopCategories(LocalDate from, LocalDate to, int limit) {
        LocalDate date = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        return categoryRankingRepository.findBySnapshotDateOrderByRankAsc(date).stream()
                .limit(limit)
                .map(r -> new TopCategoryResponse(
                        r.getCategory().getId(),
                        r.getCategory().getName(),
                        r.getBookingCount(),
                        r.getRank()))
                .toList();
    }

    @Override
    public List<TopWorkerResponse> getTopWorkers(LocalDate from, LocalDate to, int limit) {
        LocalDate date = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        return workerRankingRepository.findBySnapshotDateOrderByRankAsc(date).stream()
                .limit(limit)
                .map(r -> new TopWorkerResponse(
                        r.getWorker().getId(),
                        r.getWorker().getFirstName() + " " + r.getWorker().getLastName(),
                        r.getCompletedBookings(),
                        r.getRank()))
                .toList();
    }

    @Override
    public RatingDistributionResponse getRatingDistribution() {
        List<Review> reviews = reviewRepository.findAll().stream()
                .filter(r -> Boolean.TRUE.equals(r.getActive()) && r.getModerationStatus() == ModerationStatus.APPROVED)
                .toList();
        Map<Short, Long> distribution = new HashMap<>();
        for (short i = 1; i <= 5; i++) {
            distribution.put(i, 0L);
        }
        for (Review review : reviews) {
            distribution.merge(review.getRating(), 1L, Long::sum);
        }
        BigDecimal average = reviews.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(reviews.stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP);
        return new RatingDistributionResponse(distribution, average, reviews.size());
    }

    @Override
    @Transactional
    public void refreshSnapshot(LocalDate date) {
        Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        AnalyticsDailySnapshot snapshot = snapshotRepository.findBySnapshotDate(date)
                .orElse(AnalyticsDailySnapshot.builder().snapshotDate(date).build());
        snapshot.setTotalWorkers((int) workerRepository.count());
        snapshot.setActiveWorkers((int) workerRepository.countByVerifiedTrue());
        snapshot.setTotalCustomers((int) customerRepository.count());
        snapshot.setActiveCustomers((int) customerRepository.countByActiveTrue());
        snapshot.setBookingsCreated((int) bookingRepository.countByCreatedAtBetween(start, end));
        snapshot.setBookingsCompleted((int) bookingRepository.countByStatusAndCompletedAtBetween(
                BookingStatus.COMPLETED, start, end));
        snapshot.setBookingsCancelled((int) bookingRepository.countByStatusAndCancelledAtBetween(
                BookingStatus.CANCELLED, start, end));
        BigDecimal revenue = bookingRepository.sumFinalAmountByStatusAndCompletedAtBetween(
                BookingStatus.COMPLETED, start, end);
        snapshot.setTotalRevenue(revenue == null ? BigDecimal.ZERO : revenue);
        List<Review> dayReviews = reviewRepository.findAll().stream()
                .filter(r -> r.getCreatedAt().compareTo(start) >= 0 && r.getCreatedAt().isBefore(end))
                .toList();
        snapshot.setAvgRating(dayReviews.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(dayReviews.stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(2, RoundingMode.HALF_UP));
        snapshotRepository.save(snapshot);

        refreshCategoryRankings(date);
        refreshWorkerRankings(date);
    }

    private void refreshCategoryRankings(LocalDate date) {
        List<Category> categories = categoryRepository.findAll();
        List<AnalyticsCategoryRanking> rankings = new ArrayList<>();
        short rank = 1;
        for (Category category : categories.stream()
                .sorted(Comparator.<Category, Long>comparing(c ->
                        bookingRepository.countByCategoryIdAndStatus(c.getId(), BookingStatus.COMPLETED)).reversed())
                .limit(10)
                .toList()) {
            rankings.add(AnalyticsCategoryRanking.builder()
                    .snapshotDate(date)
                    .category(category)
                    .bookingCount((int) bookingRepository.countByCategoryIdAndStatus(category.getId(), BookingStatus.COMPLETED))
                    .rank(rank++)
                    .build());
        }
        categoryRankingRepository.saveAll(rankings);
    }

    private void refreshWorkerRankings(LocalDate date) {
        List<Worker> workers = workerRepository.findAll();
        short rank = 1;
        List<AnalyticsWorkerRanking> rankings = new ArrayList<>();
        for (Worker worker : workers.stream()
                .sorted(Comparator.<Worker, Long>comparing(w ->
                        bookingRepository.countByWorkerIdAndStatus(w.getId(), BookingStatus.COMPLETED)).reversed())
                .limit(10)
                .toList()) {
            rankings.add(AnalyticsWorkerRanking.builder()
                    .snapshotDate(date)
                    .worker(worker)
                    .completedBookings((int) bookingRepository.countByWorkerIdAndStatus(worker.getId(), BookingStatus.COMPLETED))
                    .rank(rank++)
                    .build());
        }
        workerRankingRepository.saveAll(rankings);
    }
}
