package com.bluecollar.common.transaction;

import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.availability.config.AvailabilityProperties;
import com.bluecollar.availability.dto.AvailabilitySummaryResponse;
import com.bluecollar.availability.dto.WorkerAvailabilityConfigResponse;
import com.bluecollar.availability.mapper.AvailabilityMapper;
import com.bluecollar.availability.repository.WorkerScheduleOverrideRepository;
import com.bluecollar.availability.repository.WorkerWorkingHoursRepository;
import com.bluecollar.availability.service.AvailabilityServiceImpl;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.portfolio.repository.WorkerCertificateRepository;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.portfolio.repository.WorkerPortfolioItemRepository;
import com.bluecollar.portfolio.service.ProfileCompletionServiceImpl;
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
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadOnlyTransactionIntegrityTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerWorkingHoursRepository workingHoursRepository;

    @Mock
    private WorkerScheduleOverrideRepository scheduleOverrideRepository;

    @Mock
    private AvailabilityMapper availabilityMapper;

    @Mock
    private AvailabilityProperties availabilityProperties;

    @Mock
    private WorkerPortfolioItemRepository portfolioItemRepository;

    @Mock
    private WorkerCertificateRepository certificateRepository;

    @Mock
    private WorkerIdentityDocumentRepository identityDocumentRepository;

    @InjectMocks
    private AvailabilityServiceImpl availabilityService;

    @InjectMocks
    private ProfileCompletionServiceImpl profileCompletionService;

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private UUID userId;
    private UUID workerId;
    private Worker worker;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        worker = Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .onlineStatus(OnlineStatus.ONLINE)
                .lastSeenAt(Instant.now().minusSeconds(3600)) // stale heartbeat
                .active(true)
                .build();
        worker.setId(workerId);

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUser)
                .thenReturn(new AuthenticatedUser(userId, "john@example.com", UserRole.WORKER));
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    void getMyAvailabilityDoesNotMutateWorkerInReadOnlyTransaction() {
        when(workerRepository.findByUserAccountId(userId)).thenReturn(Optional.of(worker));
        when(workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(workerId))
                .thenReturn(List.of());
        when(scheduleOverrideRepository.findByWorkerIdOrderByOverrideDateAsc(workerId))
                .thenReturn(List.of());
        when(availabilityProperties.getHeartbeatTimeoutMinutes()).thenReturn(15);
        when(availabilityMapper.toSummaryResponse(any(), any(), any()))
                .thenReturn(mock(AvailabilitySummaryResponse.class));

        WorkerAvailabilityConfigResponse response = availabilityService.getMyAvailability();

        // Verify that NO database write/save was performed during this read-only method
        verify(workerRepository, never()).save(any());
    }

    @Test
    void getPublicAvailabilityDoesNotMutateWorkerInReadOnlyTransaction() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workingHoursRepository.findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(workerId))
                .thenReturn(List.of());
        when(availabilityProperties.getHeartbeatTimeoutMinutes()).thenReturn(15);
        when(availabilityMapper.toSummaryResponse(any(), any(), any()))
                .thenReturn(mock(AvailabilitySummaryResponse.class));

        AvailabilitySummaryResponse response = availabilityService.getPublicAvailability(workerId);

        // Verify that NO database write/save was performed during this read-only method
        verify(workerRepository, never()).save(any());
    }

    @Test
    void recalculateAndSaveHasReadWriteTransactionalAnnotation() throws NoSuchMethodException {
        Method method = ProfileCompletionServiceImpl.class.getMethod("recalculateAndSave", Worker.class);
        assertTrue(method.isAnnotationPresent(Transactional.class),
                "recalculateAndSave method must be annotated with @Transactional (read-write)");
        assertFalse(method.getAnnotation(Transactional.class).readOnly(),
                "recalculateAndSave must not be read-only because it mutates entities");
    }
}
