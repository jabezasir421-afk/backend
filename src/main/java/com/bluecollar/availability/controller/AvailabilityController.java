package com.bluecollar.availability.controller;

import com.bluecollar.availability.dto.*;
import com.bluecollar.availability.service.AvailabilityService;
import com.bluecollar.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/api/v1/workers/me/availability")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<WorkerAvailabilityConfigResponse>> getMyAvailability() {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.getMyAvailability(),
                "Availability fetched successfully"
        ));
    }

    @PutMapping("/api/v1/workers/me/availability/status")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> updateOnlineStatus(
            @Valid @RequestBody UpdateOnlineStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.updateOnlineStatus(request),
                "Online status updated successfully"
        ));
    }

    @PutMapping("/api/v1/workers/me/availability/bookable")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> updateBookable(
            @Valid @RequestBody UpdateBookableRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.updateBookable(request),
                "Bookable status updated successfully"
        ));
    }

    @PutMapping("/api/v1/workers/me/availability/working-hours")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<List<WorkingHoursEntry>>> updateWorkingHours(
            @Valid @RequestBody UpdateWorkingHoursRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.updateWorkingHours(request),
                "Working hours updated successfully"
        ));
    }

    @GetMapping("/api/v1/workers/me/availability/working-hours")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<List<WorkingHoursEntry>>> getWorkingHours() {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.getWorkingHours(),
                "Working hours fetched successfully"
        ));
    }

    @PutMapping("/api/v1/workers/me/availability/vacation")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> updateVacationMode(
            @Valid @RequestBody VacationModeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.updateVacationMode(request),
                "Vacation mode updated successfully"
        ));
    }

    @PostMapping("/api/v1/workers/me/availability/overrides")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<ScheduleOverrideResponse>> addScheduleOverride(
            @Valid @RequestBody ScheduleOverrideRequest request
    ) {
        ScheduleOverrideResponse response = availabilityService.addScheduleOverride(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Schedule override added successfully"));
    }

    @DeleteMapping("/api/v1/workers/me/availability/overrides/{id}")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<Void>> removeScheduleOverride(@PathVariable UUID id) {
        availabilityService.removeScheduleOverride(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Schedule override removed successfully"));
    }

    @PostMapping("/api/v1/workers/me/availability/heartbeat")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> heartbeat() {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.heartbeat(),
                "Heartbeat recorded successfully"
        ));
    }

    @GetMapping("/api/v1/workers/{id}/availability")
    public ResponseEntity<ApiResponse<AvailabilitySummaryResponse>> getPublicAvailability(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.getPublicAvailability(id),
                "Public availability fetched successfully"
        ));
    }

    @GetMapping("/api/v1/workers/{id}/availability/slots")
    public ResponseEntity<ApiResponse<List<AvailableSlotResponse>>> getPublicSlots(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                availabilityService.getPublicSlots(id, date),
                "Available slots fetched successfully"
        ));
    }
}
