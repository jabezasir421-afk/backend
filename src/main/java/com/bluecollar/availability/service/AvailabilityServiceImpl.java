package com.bluecollar.availability.service;

import com.bluecollar.availability.config.AvailabilityProperties;
import com.bluecollar.availability.dto.*;
import com.bluecollar.availability.entity.WorkerScheduleOverride;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.availability.exception.InvalidScheduleException;
import com.bluecollar.availability.exception.WorkerNotAvailableException;
import com.bluecollar.availability.mapper.AvailabilityMapper;
import com.bluecollar.availability.repository.WorkerScheduleOverrideRepository;
import com.bluecollar.availability.repository.WorkerWorkingHoursRepository;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.worker.entity.OnlineStatus;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final int MAX_OVERRIDES = 30;
    private static final int MAX_OVERRIDE_DAYS_AHEAD = 90;

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
            BookingStatus.PENDING,
            BookingStatus.ACCEPTED,
            BookingStatus.IN_PROGRESS
    );

    private final WorkerRepository workerRepository;
    private final WorkerWorkingHoursRepository workingHoursRepository;
    private final WorkerScheduleOverrideRepository scheduleOverrideRepository;
    private final BookingRepository bookingRepository;
    private final AvailabilityMapper availabilityMapper;
    private final AvailabilityProperties availabilityProperties;

    @Override
    @Transactional(readOnly = true)
    public WorkerAvailabilityConfigResponse getMyAvailability() {
        Worker worker = findWorkerByCurrentUser();
        refreshStaleOnlineStatus(worker);
        List<WorkerWorkingHours> workingHours = workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(worker.getId());
        AvailabilitySummaryResponse summary = availabilityMapper.toSummaryResponse(
                worker,
                resolveEffectiveOnlineStatus(worker),
                workingHours
        );
        List<ScheduleOverrideResponse> overrides = scheduleOverrideRepository.findByWorkerIdOrderByOverrideDateAsc(worker.getId())
                .stream()
                .map(override -> availabilityMapper.toOverrideResponse(override, true))
                .toList();
        return new WorkerAvailabilityConfigResponse(summary, overrides);
    }

    @Override
    public AvailabilitySummaryResponse updateOnlineStatus(UpdateOnlineStatusRequest request) {
        Worker worker = findWorkerByCurrentUser();
        worker.setOnlineStatus(request.status());
        if (request.status() == OnlineStatus.ONLINE) {
            worker.setLastSeenAt(Instant.now());
        }
        workerRepository.save(worker);
        return buildSummary(worker);
    }

    @Override
    public AvailabilitySummaryResponse updateBookable(UpdateBookableRequest request) {
        Worker worker = findWorkerByCurrentUser();
        worker.setAvailable(request.bookable());
        workerRepository.save(worker);
        return buildSummary(worker);
    }

    @Override
    public List<WorkingHoursEntry> updateWorkingHours(UpdateWorkingHoursRequest request) {
        Worker worker = findWorkerByCurrentUser();
        validateWorkingHoursSchedule(request.schedule());

        workingHoursRepository.deleteByWorkerId(worker.getId());

        List<WorkerWorkingHours> savedHours = request.schedule().stream()
                .map(entry -> WorkerWorkingHours.builder()
                        .worker(worker)
                        .dayOfWeek(entry.dayOfWeek())
                        .startTime(entry.startTime())
                        .endTime(entry.endTime())
                        .build())
                .map(workingHoursRepository::save)
                .toList();

        return availabilityMapper.toWorkingHoursEntries(savedHours);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkingHoursEntry> getWorkingHours() {
        Worker worker = findWorkerByCurrentUser();
        return availabilityMapper.toWorkingHoursEntries(
                workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(worker.getId())
        );
    }

    @Override
    public AvailabilitySummaryResponse updateVacationMode(VacationModeRequest request) {
        Worker worker = findWorkerByCurrentUser();

        if (Boolean.TRUE.equals(request.enabled())) {
            if (request.vacationStart() == null || request.vacationEnd() == null) {
                throw new InvalidScheduleException("Vacation start and end dates are required when vacation mode is enabled");
            }
            if (request.vacationEnd().isBefore(request.vacationStart())) {
                throw new InvalidScheduleException("Vacation end date must be on or after start date");
            }
            worker.setVacationMode(true);
            worker.setVacationStart(request.vacationStart());
            worker.setVacationEnd(request.vacationEnd());
        } else {
            worker.setVacationMode(false);
            worker.setVacationStart(null);
            worker.setVacationEnd(null);
        }

        workerRepository.save(worker);
        return buildSummary(worker);
    }

    @Override
    public ScheduleOverrideResponse addScheduleOverride(ScheduleOverrideRequest request) {
        Worker worker = findWorkerByCurrentUser();
        validateOverrideRequest(worker, request);

        WorkerScheduleOverride override = scheduleOverrideRepository
                .findByWorkerIdAndOverrideDate(worker.getId(), request.overrideDate())
                .map(existing -> updateExistingOverride(existing, request))
                .orElseGet(() -> createOverride(worker, request));

        WorkerScheduleOverride saved = scheduleOverrideRepository.save(override);
        return availabilityMapper.toOverrideResponse(saved, true);
    }

    @Override
    public void removeScheduleOverride(UUID overrideId) {
        Worker worker = findWorkerByCurrentUser();
        WorkerScheduleOverride override = scheduleOverrideRepository.findByIdAndWorkerId(overrideId, worker.getId())
                .orElseThrow(() -> new InvalidScheduleException("Schedule override not found with id: " + overrideId));
        scheduleOverrideRepository.delete(override);
    }

    @Override
    public AvailabilitySummaryResponse heartbeat() {
        Worker worker = findWorkerByCurrentUser();
        worker.setLastSeenAt(Instant.now());
        worker.setOnlineStatus(OnlineStatus.ONLINE);
        workerRepository.save(worker);
        return buildSummary(worker);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilitySummaryResponse getPublicAvailability(UUID workerId) {
        Worker worker = findActiveWorker(workerId);
        refreshStaleOnlineStatus(worker);
        List<WorkerWorkingHours> workingHours = workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(worker.getId());
        return availabilityMapper.toSummaryResponse(worker, resolveEffectiveOnlineStatus(worker), workingHours);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getPublicSlots(UUID workerId, LocalDate date) {
        if (date == null) {
            throw new InvalidScheduleException("Date query parameter is required");
        }

        Worker worker = findActiveWorker(workerId);
        assertWorkerBookableOnDate(worker, date);

        LocalTime windowStart;
        LocalTime windowEnd;

        WorkerScheduleOverride override = scheduleOverrideRepository
                .findByWorkerIdAndOverrideDate(worker.getId(), date)
                .orElse(null);

        if (override != null) {
            if (!Boolean.TRUE.equals(override.getAvailable())) {
                return List.of();
            }
            if (override.getStartTime() == null || override.getEndTime() == null) {
                throw new InvalidScheduleException("Override for available date must include start and end times");
            }
            windowStart = override.getStartTime();
            windowEnd = override.getEndTime();
        } else {
            short dayOfWeek = (short) date.getDayOfWeek().getValue();
            WorkerWorkingHours workingHours = workingHoursRepository
                    .findByWorkerIdAndDayOfWeekAndActiveTrue(worker.getId(), dayOfWeek)
                    .orElse(null);
            if (workingHours == null) {
                return List.of();
            }
            windowStart = workingHours.getStartTime();
            windowEnd = workingHours.getEndTime();
        }

        List<TimeRange> bookedRanges = bookingRepository
                .findByWorkerIdAndScheduledDateAndStatusIn(worker.getId(), date, BLOCKING_STATUSES)
                .stream()
                .map(this::toBookedRange)
                .sorted(Comparator.comparing(TimeRange::start))
                .toList();

        return subtractBookings(windowStart, windowEnd, bookedRanges).stream()
                .map(range -> new AvailableSlotResponse(range.start(), range.end()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAvailableOnDate(UUID workerId, LocalDate date) {
        Worker worker = workerRepository.findById(workerId).orElse(null);
        if (worker == null || !Boolean.TRUE.equals(worker.getActive()) || !Boolean.TRUE.equals(worker.getAvailable())) {
            return false;
        }
        if (Boolean.TRUE.equals(worker.getVacationMode())
                && worker.getVacationStart() != null
                && worker.getVacationEnd() != null
                && !date.isBefore(worker.getVacationStart())
                && !date.isAfter(worker.getVacationEnd())) {
            return false;
        }
        try {
            return !getPublicSlots(workerId, date).isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private WorkerScheduleOverride createOverride(Worker worker, ScheduleOverrideRequest request) {
        if (scheduleOverrideRepository.countByWorkerId(worker.getId()) >= MAX_OVERRIDES) {
            throw new InvalidScheduleException("Maximum of " + MAX_OVERRIDES + " schedule overrides allowed");
        }
        return WorkerScheduleOverride.builder()
                .worker(worker)
                .overrideDate(request.overrideDate())
                .available(request.available())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .reason(trimToNull(request.reason()))
                .build();
    }

    private WorkerScheduleOverride updateExistingOverride(
            WorkerScheduleOverride existing,
            ScheduleOverrideRequest request
    ) {
        existing.setAvailable(request.available());
        existing.setStartTime(request.startTime());
        existing.setEndTime(request.endTime());
        existing.setReason(trimToNull(request.reason()));
        return existing;
    }

    private void validateOverrideRequest(Worker worker, ScheduleOverrideRequest request) {
        LocalDate maxDate = LocalDate.now().plusDays(MAX_OVERRIDE_DAYS_AHEAD);
        if (request.overrideDate().isAfter(maxDate)) {
            throw new InvalidScheduleException("Schedule overrides cannot be more than " + MAX_OVERRIDE_DAYS_AHEAD + " days ahead");
        }

        if (Boolean.TRUE.equals(request.available())) {
            if (request.startTime() == null || request.endTime() == null) {
                throw new InvalidScheduleException("Start and end times are required when override is available");
            }
            if (!request.startTime().isBefore(request.endTime())) {
                throw new InvalidScheduleException("Override start time must be before end time");
            }
        } else if (request.startTime() != null && request.endTime() != null
                && !request.startTime().isBefore(request.endTime())) {
            throw new InvalidScheduleException("Override start time must be before end time");
        }
    }

    private void validateWorkingHoursSchedule(List<WorkingHoursEntry> schedule) {
        Set<Short> days = new HashSet<>();
        for (WorkingHoursEntry entry : schedule) {
            if (!days.add(entry.dayOfWeek())) {
                throw new InvalidScheduleException("Duplicate day of week in schedule: " + entry.dayOfWeek());
            }
            if (!entry.startTime().isBefore(entry.endTime())) {
                throw new InvalidScheduleException("Working hours start time must be before end time for day " + entry.dayOfWeek());
            }
        }
    }

    private void assertWorkerBookableOnDate(Worker worker, LocalDate date) {
        if (!Boolean.TRUE.equals(worker.getActive()) || !Boolean.TRUE.equals(worker.getAvailable())) {
            throw new WorkerNotAvailableException("Worker is not accepting bookings");
        }
        if (Boolean.TRUE.equals(worker.getVacationMode())
                && worker.getVacationStart() != null
                && worker.getVacationEnd() != null
                && !date.isBefore(worker.getVacationStart())
                && !date.isAfter(worker.getVacationEnd())) {
            throw new WorkerNotAvailableException("Worker is on vacation for the requested date");
        }
    }

    private AvailabilitySummaryResponse buildSummary(Worker worker) {
        List<WorkerWorkingHours> workingHours = workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(worker.getId());
        return availabilityMapper.toSummaryResponse(worker, resolveEffectiveOnlineStatus(worker), workingHours);
    }

    private OnlineStatus resolveEffectiveOnlineStatus(Worker worker) {
        if (worker.getOnlineStatus() != OnlineStatus.ONLINE) {
            return OnlineStatus.OFFLINE;
        }
        if (worker.getLastSeenAt() == null) {
            return OnlineStatus.OFFLINE;
        }
        Duration sinceLastSeen = Duration.between(worker.getLastSeenAt(), Instant.now());
        if (sinceLastSeen.toMinutes() > availabilityProperties.getHeartbeatTimeoutMinutes()) {
            return OnlineStatus.OFFLINE;
        }
        return OnlineStatus.ONLINE;
    }

    private void refreshStaleOnlineStatus(Worker worker) {
        if (worker.getOnlineStatus() == OnlineStatus.ONLINE
                && resolveEffectiveOnlineStatus(worker) == OnlineStatus.OFFLINE) {
            worker.setOnlineStatus(OnlineStatus.OFFLINE);
            workerRepository.save(worker);
        }
    }

    private Worker findWorkerByCurrentUser() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return workerRepository.findByUserAccountId(currentUser.userAccountId())
                .orElseThrow(() -> new WorkerNotFoundException("Worker profile not found for current user"));
    }

    private Worker findActiveWorker(UUID workerId) {
        return workerRepository.findById(workerId)
                .filter(worker -> Boolean.TRUE.equals(worker.getActive()))
                .orElseThrow(() -> new WorkerNotFoundException(workerId));
    }

    private TimeRange toBookedRange(Booking booking) {
        String[] parts = booking.getTimeSlot().split("-");
        return new TimeRange(LocalTime.parse(parts[0].trim()), LocalTime.parse(parts[1].trim()));
    }

    private List<TimeRange> subtractBookings(LocalTime windowStart, LocalTime windowEnd, List<TimeRange> bookedRanges) {
        List<TimeRange> available = new ArrayList<>();
        LocalTime cursor = windowStart;

        for (TimeRange booked : bookedRanges) {
            if (booked.end().compareTo(windowStart) <= 0 || booked.start().compareTo(windowEnd) >= 0) {
                continue;
            }
            LocalTime overlapStart = booked.start().isBefore(windowStart) ? windowStart : booked.start();
            LocalTime overlapEnd = booked.end().isAfter(windowEnd) ? windowEnd : booked.end();

            if (cursor.isBefore(overlapStart)) {
                available.add(new TimeRange(cursor, overlapStart));
            }
            if (overlapEnd.isAfter(cursor)) {
                cursor = overlapEnd;
            }
        }

        if (cursor.isBefore(windowEnd)) {
            available.add(new TimeRange(cursor, windowEnd));
        }

        return available.stream()
                .filter(range -> range.start().isBefore(range.end()))
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record TimeRange(LocalTime start, LocalTime end) {
    }
}
