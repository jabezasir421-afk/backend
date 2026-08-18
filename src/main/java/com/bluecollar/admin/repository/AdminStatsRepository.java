package com.bluecollar.admin.repository;

import com.bluecollar.admin.dto.BookingsTrendResponse;
import com.bluecollar.admin.dto.CategoryDistributionResponse;
import com.bluecollar.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AdminStatsRepository extends JpaRepository<Booking, UUID> {

    @Query("""
            SELECT new com.bluecollar.admin.dto.CategoryDistributionResponse(c.name, COUNT(w.id))
            FROM Worker w
            JOIN w.category c
            WHERE w.active = true
            GROUP BY c.name
            ORDER BY c.name
            """)
    List<CategoryDistributionResponse> getCategoryDistribution();

    @Query(value = """
            SELECT b.scheduled_date, COUNT(b.id)
            FROM booking b
            WHERE (CAST(:fromDate AS date) IS NULL OR b.scheduled_date >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR b.scheduled_date <= CAST(:toDate AS date))
            GROUP BY b.scheduled_date
            ORDER BY b.scheduled_date
            """, nativeQuery = true)
    List<Object[]> getBookingsTrendRaw(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
    
    default List<BookingsTrendResponse> getBookingsTrend(LocalDate fromDate, LocalDate toDate) {
        return getBookingsTrendRaw(fromDate, toDate)
            .stream()
            .map(row -> new BookingsTrendResponse((LocalDate) row[0], ((Number) row[1]).longValue()))
            .toList();
    }
}
