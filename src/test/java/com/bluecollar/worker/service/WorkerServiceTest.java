package com.bluecollar.worker.service;

import com.bluecollar.category.entity.Category;
import com.bluecollar.category.exception.CategoryNotFoundException;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.skill.exception.SkillNotFoundException;
import com.bluecollar.skill.repository.SkillRepository;
import com.bluecollar.worker.dto.CreateWorkerRequest;
import com.bluecollar.worker.dto.UpdateWorkerRequest;
import com.bluecollar.worker.dto.WorkerResponse;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerAlreadyExistsException;
import com.bluecollar.worker.exception.WorkerNotFoundException;
import com.bluecollar.worker.mapper.WorkerMapper;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private WorkerMapper workerMapper;

    @InjectMocks
    private WorkerServiceImpl workerService;

    private UUID workerId;
    private UUID categoryId;
    private UUID skillId;
    private Category category;
    private Skill skill;
    private Worker worker;
    private WorkerResponse workerResponse;
    private CreateWorkerRequest createRequest;
    private UpdateWorkerRequest updateRequest;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        skillId = UUID.randomUUID();

        category = new Category();
        category.setId(categoryId);
        category.setName("Plumbing");

        skill = new Skill();
        skill.setId(skillId);
        skill.setName("Pipe fitting");

        worker = Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .email("john.doe@example.com")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Experienced plumber")
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .skills(Set.of(skill))
                .build();

        workerResponse = new WorkerResponse(
                workerId,
                "John",
                "Doe",
                "+1234567890",
                "john.doe@example.com",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Experienced plumber",
                BigDecimal.valueOf(50),
                true,
                false,
                true,
                categoryId,
                "Plumbing",
                List.of(),
                BigDecimal.ZERO,
                0,
                null,
                null
        );

        createRequest = new CreateWorkerRequest(
                "John",
                "Doe",
                "+1234567890",
                "john.doe@example.com",
                Gender.MALE,
                LocalDate.of(1990, 1, 1),
                5,
                "Experienced plumber",
                BigDecimal.valueOf(50),
                categoryId,
                Set.of(skillId)
        );

        updateRequest = new UpdateWorkerRequest(
                "Jane",
                "Smith",
                "+0987654321",
                "jane.smith@example.com",
                Gender.FEMALE,
                LocalDate.of(1992, 6, 15),
                3,
                "Expert electrician",
                BigDecimal.valueOf(60),
                categoryId,
                Set.of(skillId)
        );
    }

    @Test
    void createWorkerShouldCreateAndReturnResponseWhenPhoneAndEmailAreAvailable() {
        Worker savedWorker = Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+1234567890")
                .email("john.doe@example.com")
                .category(category)
                .build();

        when(workerRepository.existsByPhoneNumber("+1234567890")).thenReturn(false);
        when(workerRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(skillRepository.findAllById(any())).thenReturn(List.of(skill));
        when(workerMapper.toEntity(any(), any(), any())).thenReturn(worker);
        when(workerRepository.save(worker)).thenReturn(savedWorker);
        when(workerMapper.toResponse(savedWorker)).thenReturn(workerResponse);

        WorkerResponse result = workerService.createWorker(createRequest);

        assertEquals(workerResponse, result);
        verify(workerRepository).existsByPhoneNumber("+1234567890");
        verify(workerRepository).existsByEmailIgnoreCase("john.doe@example.com");
        verify(categoryRepository).findById(categoryId);
        verify(workerRepository).save(worker);
    }

    @Test
    void createWorkerShouldThrowWhenPhoneNumberAlreadyExists() {
        when(workerRepository.existsByPhoneNumber("+1234567890")).thenReturn(true);

        assertThrows(WorkerAlreadyExistsException.class, () -> workerService.createWorker(createRequest));
        verify(workerRepository).existsByPhoneNumber("+1234567890");
        verify(workerRepository, never()).save(any());
    }

    @Test
    void createWorkerShouldThrowWhenEmailAlreadyExists() {
        when(workerRepository.existsByPhoneNumber("+1234567890")).thenReturn(false);
        when(workerRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(true);

        assertThrows(WorkerAlreadyExistsException.class, () -> workerService.createWorker(createRequest));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void createWorkerShouldThrowWhenCategoryNotFound() {
        when(workerRepository.existsByPhoneNumber("+1234567890")).thenReturn(false);
        when(workerRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> workerService.createWorker(createRequest));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void createWorkerShouldThrowWhenSkillNotFound() {
        UUID missingSkillId = UUID.randomUUID();
        CreateWorkerRequest requestWithMissingSkill = new CreateWorkerRequest(
                "John", "Doe", "+1234567890", "john.doe@example.com",
                Gender.MALE, LocalDate.of(1990, 1, 1), 5, "Bio",
                BigDecimal.valueOf(50),
                categoryId, Set.of(missingSkillId)
        );

        when(workerRepository.existsByPhoneNumber("+1234567890")).thenReturn(false);
        when(workerRepository.existsByEmailIgnoreCase("john.doe@example.com")).thenReturn(false);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(skillRepository.findAllById(any())).thenReturn(List.of());

        assertThrows(SkillNotFoundException.class, () -> workerService.createWorker(requestWithMissingSkill));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void getAllWorkersShouldReturnPageOfResponses() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Worker> page = new PageImpl<>(List.of(worker), pageable, 1);

        when(workerRepository.findAll(pageable)).thenReturn(page);
        when(workerMapper.toResponse(worker)).thenReturn(workerResponse);

        Page<WorkerResponse> result = workerService.getAllWorkers(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(workerResponse, result.getContent().getFirst());
        verify(workerRepository).findAll(pageable);
    }

    @Test
    void getWorkerByIdShouldReturnWorkerWhenItExists() {
        worker.setId(workerId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerMapper.toResponse(worker)).thenReturn(workerResponse);

        WorkerResponse result = workerService.getWorkerById(workerId);

        assertEquals(workerResponse, result);
        verify(workerRepository).findById(workerId);
    }

    @Test
    void getWorkerByIdShouldThrowWhenWorkerDoesNotExist() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(WorkerNotFoundException.class, () -> workerService.getWorkerById(workerId));
        verify(workerRepository).findById(workerId);
    }

    @Test
    void updateWorkerShouldUpdateAndReturnResponseWhenWorkerExists() {
        worker.setId(workerId);
        Worker updatedWorker = Worker.builder()
                .firstName("Jane")
                .lastName("Smith")
                .phoneNumber("+0987654321")
                .email("jane.smith@example.com")
                .category(category)
                .build();
        WorkerResponse updatedResponse = new WorkerResponse(
                workerId, "Jane", "Smith", "+0987654321", "jane.smith@example.com",
                Gender.FEMALE, LocalDate.of(1992, 6, 15), 3, "Expert electrician",
                BigDecimal.valueOf(60), true, true, true, categoryId, "Plumbing",
                List.of(), BigDecimal.ZERO, 0, null, null
        );

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.findByPhoneNumber("+0987654321")).thenReturn(Optional.empty());
        when(workerRepository.findByEmailIgnoreCase("jane.smith@example.com")).thenReturn(Optional.empty());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(skillRepository.findAllById(any())).thenReturn(List.of(skill));
        when(workerRepository.save(worker)).thenReturn(updatedWorker);
        when(workerMapper.toResponse(updatedWorker)).thenReturn(updatedResponse);

        WorkerResponse result = workerService.updateWorker(workerId, updateRequest);

        assertEquals(updatedResponse, result);
        verify(workerRepository).findById(workerId);
        verify(workerRepository).save(worker);
    }

    @Test
    void updateWorkerShouldThrowWhenWorkerDoesNotExist() {
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(WorkerNotFoundException.class, () -> workerService.updateWorker(workerId, updateRequest));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void updateWorkerShouldThrowWhenPhoneNumberBelongsToAnotherWorker() {
        UUID otherId = UUID.randomUUID();
        Worker other = Worker.builder().phoneNumber("+0987654321").build();
        other.setId(otherId);
        worker.setId(workerId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.findByPhoneNumber("+0987654321")).thenReturn(Optional.of(other));

        assertThrows(WorkerAlreadyExistsException.class, () -> workerService.updateWorker(workerId, updateRequest));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void updateWorkerShouldThrowWhenEmailBelongsToAnotherWorker() {
        UUID otherId = UUID.randomUUID();
        Worker other = Worker.builder().email("jane.smith@example.com").build();
        other.setId(otherId);
        worker.setId(workerId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.findByPhoneNumber("+0987654321")).thenReturn(Optional.empty());
        when(workerRepository.findByEmailIgnoreCase("jane.smith@example.com")).thenReturn(Optional.of(other));

        assertThrows(WorkerAlreadyExistsException.class, () -> workerService.updateWorker(workerId, updateRequest));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void updateWorkerShouldThrowWhenCategoryNotFound() {
        worker.setId(workerId);

        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(workerRepository.findByPhoneNumber(any())).thenReturn(Optional.empty());
        when(workerRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> workerService.updateWorker(workerId, updateRequest));
        verify(workerRepository, never()).save(any());
    }

    @Test
    void deleteWorkerShouldDeleteWhenWorkerExists() {
        when(workerRepository.existsById(workerId)).thenReturn(true);

        workerService.deleteWorker(workerId);

        verify(workerRepository).existsById(workerId);
        verify(workerRepository).deleteById(workerId);
    }

    @Test
    void deleteWorkerShouldThrowWhenWorkerDoesNotExist() {
        when(workerRepository.existsById(workerId)).thenReturn(false);

        assertThrows(WorkerNotFoundException.class, () -> workerService.deleteWorker(workerId));
        verify(workerRepository, never()).deleteById(any());
    }
}
