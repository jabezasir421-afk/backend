package com.bluecollar.availability.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.availability.config.AvailabilityProperties;
import com.bluecollar.availability.dto.*;
import com.bluecollar.availability.entity.WorkerScheduleOverride;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.availability.exception.InvalidScheduleException;
import com.bluecollar.availability.mapper.AvailabilityMapper;
import com.bluecollar.availability.repository.WorkerScheduleOverrideRepository;
import com.bluecollar.availability.repository.WorkerWorkingHoursRepository;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.worker.entity.OnlineStatus;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerWorkingHoursRepository workingHoursRepository;

    @Mock
    private WorkerScheduleOverrideRepository scheduleOverrideRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AvailabilityMapper availabilityMapper;

    @Mock
    private AvailabilityProperties availabilityProperties;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID workerId;
    private UUID userAccountId;
    private UUID overrideId;
    private UserAccount userAccount;
    private Worker worker;
    private AvailabilitySummaryResponse summaryResponse;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        userAccountId = UUID.randomUUID();
        overrideId = UUID.randomUUID();

        userAccount = UserAccount.builder()
                .email("worker@example.com")
                .role(UserRole.WORKER)
                .build();
        userAccount.setId(userAccountId);

        worker = Worker.builder()
                .userAccount(userAccount)
                .firstName("Bob")
                .lastName("Builder")
                .available(true)
                .active(true)
                .onlineStatus(OnlineStatus.OFFLINE)
                .vacationMode(false)
                .build();
        worker.setId(workerId);

        summaryResponse = new AvailabilitySummaryResponse(
                true, OnlineStatus.ONLINE, false, null, null, List.of()
        );

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(userAccountId, "worker@example.com", UserRole.WORKER));
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void updateOnlineStatusShouldSetStatusAndLastSeenWhenOnline() {
        UpdateOnlineStatusRequest request = new UpdateOnlineStatusRequest(OnlineStatus.ONLINE);

        when(availabilityProperties.getHeartbeatTimeoutMinutes()).thenReturn(15);
        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenReturn(worker);
        when(workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(workerId)).thenReturn(List.of());
        when(availabilityMapper.toSummaryResponse(eq(worker), eq(OnlineStatus.ONLINE), any())).thenReturn(summaryResponse);

        AvailabilitySummaryResponse result = availabilityService.updateOnlineStatus(request);

        assertEquals(summaryResponse, result);
        assertEquals(OnlineStatus.ONLINE, worker.getOnlineStatus());
        assertTrue(worker.getLastSeenAt() != null);
        verify(workerRepository).save(worker);
    }

    @Test
    void updateBookableShouldUpdateWorkerAvailability() {
        UpdateBookableRequest request = new UpdateBookableRequest(false);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenReturn(worker);
        when(workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(workerId)).thenReturn(List.of());
        when(availabilityMapper.toSummaryResponse(eq(worker), any(), any())).thenReturn(summaryResponse);

        availabilityService.updateBookable(request);

        assertFalse(worker.getAvailable());
        verify(workerRepository).save(worker);
    }

    @Test
    void updateWorkingHoursShouldReplaceExistingSchedule() {
        WorkingHoursEntry monday = new WorkingHoursEntry((short) 1, LocalTime.of(9, 0), LocalTime.of(17, 0));
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest(List.of(monday));
        WorkerWorkingHours savedHours = WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build();

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(workingHoursRepository.save(any(WorkerWorkingHours.class))).thenReturn(savedHours);
        when(availabilityMapper.toWorkingHoursEntries(any())).thenReturn(List.of(monday));

        List<WorkingHoursEntry> result = availabilityService.updateWorkingHours(request);

        assertEquals(1, result.size());
        verify(workingHoursRepository).deleteByWorkerId(workerId);
        verify(workingHoursRepository).save(any(WorkerWorkingHours.class));
    }

    @Test
    void updateWorkingHoursShouldThrowWhenDuplicateDayOfWeek() {
        WorkingHoursEntry mondayMorning = new WorkingHoursEntry((short) 1, LocalTime.of(9, 0), LocalTime.of(12, 0));
        WorkingHoursEntry mondayAfternoon = new WorkingHoursEntry((short) 1, LocalTime.of(13, 0), LocalTime.of(17, 0));
        UpdateWorkingHoursRequest request = new UpdateWorkingHoursRequest(List.of(mondayMorning, mondayAfternoon));

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidScheduleException.class, () -> availabilityService.updateWorkingHours(request));
        verify(workingHoursRepository, never()).deleteByWorkerId(any());
    }

    @Test
    void updateVacationModeShouldEnableVacationWithDates() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(7);
        VacationModeRequest request = new VacationModeRequest(true, start, end);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenReturn(worker);
        when(workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(workerId)).thenReturn(List.of());
        when(availabilityMapper.toSummaryResponse(eq(worker), any(), any())).thenReturn(summaryResponse);

        availabilityService.updateVacationMode(request);

        assertTrue(worker.getVacationMode());
        assertEquals(start, worker.getVacationStart());
        assertEquals(end, worker.getVacationEnd());
        verify(workerRepository).save(worker);
    }

    @Test
    void updateVacationModeShouldThrowWhenEnabledWithoutDates() {
        VacationModeRequest request = new VacationModeRequest(true, null, null);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidScheduleException.class, () -> availabilityService.updateVacationMode(request));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void addScheduleOverrideShouldCreateOverrideWhenValid() {
        LocalDate overrideDate = LocalDate.now().plusDays(3);
        ScheduleOverrideRequest request = new ScheduleOverrideRequest(
                overrideDate, true, LocalTime.of(10, 0), LocalTime.of(14, 0), "Half day"
        );
        WorkerScheduleOverride savedOverride = WorkerScheduleOverride.builder()
                .worker(worker)
                .overrideDate(overrideDate)
                .available(true)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(14, 0))
                .reason("Half day")
                .build();
        savedOverride.setId(overrideId);
        ScheduleOverrideResponse overrideResponse = new ScheduleOverrideResponse(
                overrideId, overrideDate, true,
                new WorkingHoursEntry((short) overrideDate.getDayOfWeek().getValue(), LocalTime.of(10, 0), LocalTime.of(14, 0)),
                "Half day"
        );

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(scheduleOverrideRepository.findByWorkerIdAndOverrideDate(workerId, overrideDate)).thenReturn(Optional.empty());
        when(scheduleOverrideRepository.countByWorkerId(workerId)).thenReturn(0L);
        when(scheduleOverrideRepository.save(any(WorkerScheduleOverride.class))).thenReturn(savedOverride);
        when(availabilityMapper.toOverrideResponse(savedOverride, true)).thenReturn(overrideResponse);

        ScheduleOverrideResponse result = availabilityService.addScheduleOverride(request);

        assertEquals(overrideResponse, result);
        verify(scheduleOverrideRepository).save(any(WorkerScheduleOverride.class));
    }

    @Test
    void addScheduleOverrideShouldThrowWhenAvailableWithoutTimes() {
        ScheduleOverrideRequest request = new ScheduleOverrideRequest(
                LocalDate.now().plusDays(2), true, null, null, null
        );

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidScheduleException.class, () -> availabilityService.addScheduleOverride(request));
        verify(scheduleOverrideRepository, never()).save(any());
    }

    @Test
    void removeScheduleOverrideShouldDeleteExistingOverride() {
        WorkerScheduleOverride override = WorkerScheduleOverride.builder()
                .worker(worker)
                .overrideDate(LocalDate.now().plusDays(1))
                .available(false)
                .build();
        override.setId(overrideId);

        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(scheduleOverrideRepository.findByIdAndWorkerId(overrideId, workerId)).thenReturn(Optional.of(override));

        availabilityService.removeScheduleOverride(overrideId);

        verify(scheduleOverrideRepository).delete(override);
    }

    @Test
    void heartbeatShouldSetOnlineStatusAndLastSeen() {
        when(availabilityProperties.getHeartbeatTimeoutMinutes()).thenReturn(15);
        when(workerRepository.findByUserAccountId(userAccountId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenReturn(worker);
        when(workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(workerId)).thenReturn(List.of());
        when(availabilityMapper.toSummaryResponse(eq(worker), eq(OnlineStatus.ONLINE), any())).thenReturn(summaryResponse);

        AvailabilitySummaryResponse result = availabilityService.heartbeat();

        assertEquals(summaryResponse, result);
        assertEquals(OnlineStatus.ONLINE, worker.getOnlineStatus());
        assertTrue(worker.getLastSeenAt() != null);
        verify(workerRepository).save(worker);
    }

    @Test
    void isAvailableOnDateShouldReturnFalseWhenWorkerInactive() {
        worker.setActive(false);
        LocalDate date = LocalDate.now().plusDays(1);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertFalse(availabilityService.isAvailableOnDate(workerId, date));
    }

    @Test
    void isAvailableOnDateShouldReturnFalseWhenWorkerOnVacation() {
        LocalDate date = LocalDate.now().plusDays(2);
        worker.setVacationMode(true);
        worker.setVacationStart(LocalDate.now());
        worker.setVacationEnd(LocalDate.now().plusDays(5));

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertFalse(availabilityService.isAvailableOnDate(workerId, date));
    }

    @Test
    void isAvailableOnDateShouldReturnTrueWhenWorkingHoursExistAndNoBookings() {
        LocalDate date = LocalDate.now().plusDays(1);
        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        WorkerWorkingHours workingHours = WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek(dayOfWeek)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build();

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(scheduleOverrideRepository.findByWorkerIdAndOverrideDate(workerId, date)).thenReturn(Optional.empty());
        when(workingHoursRepository.findByWorkerIdAndDayOfWeekAndActiveTrue(workerId, dayOfWeek))
                .thenReturn(Optional.of(workingHours));
        when(bookingRepository.findByWorkerIdAndScheduledDateAndStatusIn(eq(workerId), eq(date), any()))
                .thenReturn(List.of());

        assertTrue(availabilityService.isAvailableOnDate(workerId, date));
    }
}
