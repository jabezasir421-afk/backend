package com.bluecollar.booking.service;

import com.bluecollar.booking.dto.*;
import com.bluecollar.booking.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;

public interface BookingService {

    BookingResponse createBooking(CreateBookingRequest request);

    Page<BookingResponse> getMyBookings(BookingStatus status, Pageable pageable);

    BookingResponse getBookingById(UUID id);

    BookingResponse acceptBooking(UUID id);

    BookingResponse startBooking(UUID id);

    BookingResponse rejectBooking(UUID id, RejectBookingRequest request);

    BookingResponse cancelBooking(UUID id, CancelBookingRequest request);

    BookingResponse completeBooking(UUID id, CompleteBookingRequest request);

    Page<BookingResponse> getAllBookings(
            BookingStatus status,
            UUID categoryId,
            UUID workerId,
            UUID customerId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    );
}
