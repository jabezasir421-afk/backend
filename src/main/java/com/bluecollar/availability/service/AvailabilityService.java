package com.bluecollar.availability.service;

import com.bluecollar.availability.dto.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityService {

    WorkerAvailabilityConfigResponse getMyAvailability();

    AvailabilitySummaryResponse updateOnlineStatus(UpdateOnlineStatusRequest request);

    AvailabilitySummaryResponse updateBookable(UpdateBookableRequest request);

    List<WorkingHoursEntry> updateWorkingHours(UpdateWorkingHoursRequest request);

    List<WorkingHoursEntry> getWorkingHours();

    AvailabilitySummaryResponse updateVacationMode(VacationModeRequest request);

    ScheduleOverrideResponse addScheduleOverride(ScheduleOverrideRequest request);

    void removeScheduleOverride(UUID overrideId);

    AvailabilitySummaryResponse heartbeat();

    AvailabilitySummaryResponse getPublicAvailability(UUID workerId);

    List<AvailableSlotResponse> getPublicSlots(UUID workerId, LocalDate date);

    boolean isAvailableOnDate(UUID workerId, LocalDate date);
}
