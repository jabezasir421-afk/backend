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

    @Query("""
            SELECT new com.bluecollar.admin.dto.BookingsTrendResponse(b.scheduledDate, COUNT(b.id))
            FROM Booking b
            WHERE (:fromDate IS NULL OR b.scheduledDate >= :fromDate)
              AND (:toDate IS NULL OR b.scheduledDate <= :toDate)
            GROUP BY b.scheduledDate
            ORDER BY b.scheduledDate
            """)
    List<BookingsTrendResponse> getBookingsTrend(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );
}
