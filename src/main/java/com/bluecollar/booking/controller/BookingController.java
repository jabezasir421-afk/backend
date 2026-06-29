package com.bluecollar.booking.controller;

import com.bluecollar.booking.dto.*;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.service.BookingService;
import com.bluecollar.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(booking, "Booking created successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getMyBookings(status, pageable), "Bookings fetched successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingById(id), "Booking fetched successfully"));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<BookingResponse>> acceptBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.acceptBooking(id), "Booking accepted successfully"));
    }

    @PutMapping("/{id}/start")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<BookingResponse>> startBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.startBooking(id), "Booking started successfully"));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<BookingResponse>> rejectBooking(
            @PathVariable UUID id,
            @Valid @RequestBody RejectBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.rejectBooking(id, request), "Booking rejected successfully"));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER')")
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable UUID id,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.cancelBooking(id, request), "Booking cancelled successfully"));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.completeBooking(id, request), "Booking completed successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID workerId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getAllBookings(status, categoryId, workerId, customerId, fromDate, toDate, pageable),
                "Bookings fetched successfully"
        ));
    }
}
