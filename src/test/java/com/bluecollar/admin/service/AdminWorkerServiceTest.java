package com.bluecollar.admin.service;

import com.bluecollar.portfolio.entity.VerificationStatus;
import com.bluecollar.portfolio.repository.WorkerIdentityDocumentRepository;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.mapper.WorkerMapper;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWorkerServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerMapper workerMapper;

    @Mock
    private WorkerIdentityDocumentRepository identityDocumentRepository;

    @InjectMocks
    private AdminWorkerServiceImpl adminWorkerService;

    private UUID workerId;
    private Worker worker;
    private WorkerResponse workerResponse;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        worker = Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .email("john.doe@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Experienced worker")
                .hourlyRate(new BigDecimal("50.00"))
                .available(true)
                .verified(false)
                .active(true)
                .averageRating(BigDecimal.ZERO)
                .reviewCount(0)
                .build();
        worker.setId(workerId);

        workerResponse = new WorkerResponse(
                workerId,
                "John",
                "Doe",
                "+1234567890",
                "john.doe@example.com",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Experienced worker",
                new BigDecimal("50.00"),
                true,
                false,
                true,
                null,
                null,
                List.of(),
                BigDecimal.ZERO,
                0,
                Instant.now(),
                Instant.now()
        );
    }

    @Test
    void verifyWorkerShouldThrowWhenIdentityDocumentIsNotVerified() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.existsByWorkerIdAndVerificationStatusAndActiveTrue(
                workerId,
                VerificationStatus.VERIFIED
        )).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> adminWorkerService.verifyWorker(workerId));
    }

    @Test
    void verifyWorkerShouldVerifyWorkerWhenIdentityDocumentExists() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(identityDocumentRepository.existsByWorkerIdAndVerificationStatusAndActiveTrue(
                workerId,
                VerificationStatus.VERIFIED
        )).thenReturn(true);
        when(workerRepository.save(worker)).thenAnswer(invocation -> {
            Worker saved = invocation.getArgument(0);
            saved.setVerified(true);
            return saved;
        });
        when(workerMapper.toResponse(worker)).thenReturn(new WorkerResponse(
                workerId,
                "John",
                "Doe",
                "+1234567890",
                "john.doe@example.com",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Experienced worker",
                new BigDecimal("50.00"),
                true,
                true,
                true,
                null,
                null,
                List.of(),
                BigDecimal.ZERO,
                0,
                Instant.now(),
                Instant.now()
        ));

        WorkerResponse result = adminWorkerService.verifyWorker(workerId);

        assertTrue(result.verified());
        verify(workerRepository).save(worker);
    }

    @Test
    void verifyWorkerShouldThrowWhenWorkerIsInactive() {
        worker.setActive(false);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertThrows(IllegalStateException.class, () -> adminWorkerService.verifyWorker(workerId));
    }

    @Test
    void unverifyWorkerShouldSetVerifiedFalse() {
        worker.setVerified(true);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenAnswer(invocation -> {
            Worker saved = invocation.getArgument(0);
            saved.setVerified(false);
            return saved;
        });
        when(workerMapper.toResponse(worker)).thenReturn(workerResponse);

        WorkerResponse result = adminWorkerService.unverifyWorker(workerId);

        assertFalse(result.verified());
        verify(workerRepository).save(worker);
    }

    @Test
    void deactivateWorkerShouldSetActiveAndAvailableFalse() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.save(worker)).thenAnswer(invocation -> {
            Worker saved = invocation.getArgument(0);
            saved.setActive(false);
            saved.setAvailable(false);
            return saved;
        });
        when(workerMapper.toResponse(worker)).thenReturn(new WorkerResponse(
                workerId,
                "John",
                "Doe",
                "+1234567890",
                "john.doe@example.com",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Experienced worker",
                new BigDecimal("50.00"),
                false,
                false,
                false,
                null,
                null,
                List.of(),
                BigDecimal.ZERO,
                0,
                Instant.now(),
                Instant.now()
        ));

        WorkerResponse result = adminWorkerService.deactivateWorker(workerId);

        assertFalse(result.active());
        assertFalse(result.available());
        verify(workerRepository).save(worker);
    }

    @Test
    void verifyWorkerShouldThrowWhenWorkerDoesNotExist() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(WorkerNotFoundException.class, () -> adminWorkerService.verifyWorker(workerId));
    }
}
