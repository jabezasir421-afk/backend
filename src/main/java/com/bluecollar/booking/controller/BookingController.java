package com.bluecollar.booking.controller;

import com.bluecollar.booking.dto.*;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.service.BookingService;
import com.bluecollar.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Bookings", description = "Booking management for customers and workers")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Create a new booking",
            description = "Create a booking request for a worker. Only customers can create bookings.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse booking = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(booking, "Booking created successfully"));
    }

    @GetMapping("/customers/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Get customer's bookings",
            description = "Retrieve all bookings created by the authenticated customer",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyCustomerBookings(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getMyBookings(status, pageable), "Customer bookings fetched successfully"));
    }

    @GetMapping("/workers/me")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Get worker's bookings",
            description = "Retrieve all bookings assigned to the authenticated worker",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyWorkerBookings(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getMyBookings(status, pageable), "Worker bookings fetched successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER')")
    @Operation(
            summary = "Get authenticated user's bookings",
            description = "Retrieve bookings for the authenticated user (customer or worker). " +
                    "Customers see their created bookings, workers see their assigned bookings.",
            security = @SecurityRequirement(name = "bearerAuth"),
            deprecated = true
    )
    public ResponseEntity<ApiResponse<Page<BookingResponse>>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getMyBookings(status, pageable), "Bookings fetched successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER', 'ADMIN')")
    @Operation(
            summary = "Get booking details",
            description = "Retrieve details for a specific booking. Only the customer, worker, or admin can view a booking.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> getBookingById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getBookingById(id), "Booking fetched successfully"));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Accept a booking",
            description = "Worker accepts a pending booking request",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> acceptBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.acceptBooking(id), "Booking accepted successfully"));
    }

    @PutMapping("/{id}/start")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Start a booking",
            description = "Worker marks an accepted booking as started/in-progress",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> startBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.startBooking(id), "Booking started successfully"));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Reject a booking",
            description = "Worker rejects a pending booking request with optional reason",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> rejectBooking(
            @PathVariable UUID id,
            @Valid @RequestBody RejectBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.rejectBooking(id, request), "Booking rejected successfully"));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WORKER')")
    @Operation(
            summary = "Cancel a booking",
            description = "Cancel a booking (customer or worker can cancel). May require cancellation reason.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> cancelBooking(
            @PathVariable UUID id,
            @Valid @RequestBody CancelBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.cancelBooking(id, request), "Booking cancelled successfully"));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('WORKER')")
    @Operation(
            summary = "Complete a booking",
            description = "Worker marks an in-progress booking as completed. May include final amount and details.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> completeBooking(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.completeBooking(id, request), "Booking completed successfully"));
    }

    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Reschedule a booking",
            description = "Customer reschedules a pending booking to a different date/time. Only allowed before worker accepts. " +
                    "Worker availability will be validated for the new time slot.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<BookingResponse>> rescheduleBooking(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleBookingRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.rescheduleBooking(id, request), "Booking rescheduled successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Search all bookings (admin only)",
            description = "Admin-only endpoint requiring JWT token with ADMIN role. Searches all bookings across the platform. " +
                    "Supports filtering by status, category, worker, customer, and date range. " +
                    "WARNING: Exposing this to non-admins would allow unauthorized data access.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
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
