package com.bluecollar.booking.repository;

import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Page<Booking> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Booking> findByWorkerId(UUID workerId, Pageable pageable);

    Page<Booking> findByCustomerIdAndStatus(UUID customerId, BookingStatus status, Pageable pageable);

    Page<Booking> findByWorkerIdAndStatus(UUID workerId, BookingStatus status, Pageable pageable);

    long countByStatus(BookingStatus status);

    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.status IN :statuses
            """)
    long countByStatusIn(@Param("statuses") java.util.Collection<BookingStatus> statuses);

    @Query("""
            SELECT COUNT(b) FROM Booking b
            WHERE b.status = com.bluecollar.booking.entity.BookingStatus.COMPLETED
            """)
    long countCompleted();

    @Query("""
            SELECT b FROM Booking b
            WHERE (:status IS NULL OR b.status = :status)
              AND (:categoryId IS NULL OR b.category.id = :categoryId)
              AND (:workerId IS NULL OR b.worker.id = :workerId)
              AND (:customerId IS NULL OR b.customer.id = :customerId)
              AND (CASE WHEN :fromDate IS NULL THEN true ELSE b.scheduledDate >= :fromDate END = true)
              AND (CASE WHEN :toDate IS NULL THEN true ELSE b.scheduledDate <= :toDate END = true)
            """)
    Page<Booking> findAllWithFilters(
            @Param("status") BookingStatus status,
            @Param("categoryId") UUID categoryId,
            @Param("workerId") UUID workerId,
            @Param("customerId") UUID customerId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    List<Booking> findByWorkerIdAndScheduledDateAndStatusIn(
            UUID workerId,
            LocalDate scheduledDate,
            java.util.Collection<BookingStatus> statuses
    );

    @Query("""
            SELECT COUNT(b) > 0 FROM Booking b
            WHERE b.worker.id = :workerId
              AND b.scheduledDate = :scheduledDate
              AND b.status IN :statuses
              AND (:excludeBookingId IS NULL OR b.id <> :excludeBookingId)
              AND (b.startTime < :endTime AND b.endTime > :startTime)
            """)
    boolean existsOverlappingBooking(
            @Param("workerId") UUID workerId,
            @Param("scheduledDate") LocalDate scheduledDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("statuses") java.util.Collection<BookingStatus> statuses,
            @Param("excludeBookingId") UUID excludeBookingId
    );

    long countByCreatedAtBetween(Instant from, Instant to);

    long countByStatusAndCompletedAtBetween(BookingStatus status, Instant from, Instant to);

    long countByStatusAndCancelledAtBetween(BookingStatus status, Instant from, Instant to);

    long countByCategoryIdAndStatus(UUID categoryId, BookingStatus status);

    long countByWorkerIdAndStatus(UUID workerId, BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.finalAmount), 0) FROM Booking b WHERE b.status = :status")
    BigDecimal sumFinalAmountByStatus(@Param("status") BookingStatus status);

    @Query("""
            SELECT COALESCE(SUM(b.finalAmount), 0) FROM Booking b
            WHERE b.status = :status AND b.completedAt >= :from AND b.completedAt < :to
            """)
    BigDecimal sumFinalAmountByStatusAndCompletedAtBetween(
            @Param("status") BookingStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            SELECT b.category.id AS categoryId, b.category.name AS categoryName, COUNT(b) AS bookingCount
            FROM Booking b
            WHERE b.status = com.bluecollar.booking.entity.BookingStatus.COMPLETED
              AND b.category.active = true
              AND (CAST(:fromDate AS date) IS NULL OR b.scheduledDate >= :fromDate)
              AND (CAST(:toDate AS date) IS NULL OR b.scheduledDate <= :toDate)
            GROUP BY b.category.id, b.category.name
            ORDER BY COUNT(b) DESC, b.category.name ASC
            """)
    List<CategoryBookingStatsProjection> findTopCategoriesWithDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            SELECT b.worker.id AS workerId, b.worker.firstName AS firstName, b.worker.lastName AS lastName, COUNT(b) AS completedBookings
            FROM Booking b
            WHERE b.status = com.bluecollar.booking.entity.BookingStatus.COMPLETED
              AND b.worker.active = true
              AND (CAST(:fromDate AS date) IS NULL OR b.scheduledDate >= :fromDate)
              AND (CAST(:toDate AS date) IS NULL OR b.scheduledDate <= :toDate)
            GROUP BY b.worker.id, b.worker.firstName, b.worker.lastName
            ORDER BY COUNT(b) DESC, b.worker.lastName ASC
            """)
    List<WorkerBookingStatsProjection> findTopWorkersWithDateRange(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );

    @Query("""
            SELECT b.category AS category, COUNT(b) AS bookingCount
            FROM Booking b
            WHERE b.status = :status
              AND b.category.active = true
            GROUP BY b.category
            ORDER BY COUNT(b) DESC, b.category.name ASC
            """)
    List<CategoryRankingProjection> findTopCategoriesForRanking(
            @Param("status") BookingStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT b.worker AS worker, COUNT(b) AS bookingCount
            FROM Booking b
            WHERE b.status = :status
              AND b.worker.active = true
            GROUP BY b.worker
            ORDER BY COUNT(b) DESC, b.worker.lastName ASC
            """)
    List<WorkerRankingProjection> findTopWorkersForRanking(
            @Param("status") BookingStatus status,
            Pageable pageable
    );
}
