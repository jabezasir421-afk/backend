package com.bluecollar.search.service;

import com.bluecollar.availability.service.AvailabilityService;
import com.bluecollar.category.entity.Category;
import com.bluecollar.search.dto.WorkerSearchResponse;
import com.bluecollar.search.dto.WorkerSearchResultResponse;
import com.bluecollar.skill.entity.Skill;
import com.bluecollar.storage.service.StoredFileService;
import com.bluecollar.worker.entity.Worker;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerSearchServiceTest {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private StoredFileService storedFileService;

    @Mock
    private AvailabilityService availabilityService;

    @InjectMocks
    private WorkerSearchServiceImpl workerSearchService;

    private UUID workerId;
    private UUID categoryId;
    private UUID skillId;
    private Category category;
    private Skill skill;
    private Worker worker;
    private WorkerSearchResultResponse searchResult;

    @BeforeEach
    void setUp() {
        workerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        skillId = UUID.randomUUID();

        category = Category.builder()
                .name("Plumbing")
                .active(true)
                .build();
        category.setId(categoryId);

        skill = Skill.builder()
                .name("Pipe fitting")
                .active(true)
                .build();
        skill.setId(skillId);

        worker = Worker.builder()
                .firstName("Bob")
                .lastName("Builder")
                .primaryCity("New York")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .averageRating(BigDecimal.valueOf(4.5))
                .reviewCount(10)
                .available(true)
                .verified(true)
                .experienceYears(5)
                .skills(new LinkedHashSet<>(Set.of(skill)))
                .profileCompletionPercent((short) 80)
                .active(true)
                .build();
        worker.setId(workerId);

        searchResult = new WorkerSearchResultResponse(
                workerId,
                "Bob Builder",
                "New York",
                "Plumbing",
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(4.5),
                10,
                true,
                true,
                "http://files/photo.jpg",
                List.of("Pipe fitting"),
                80
        );
    }

    @Test
    void searchWorkersShouldReturnFilteredResultsWithPagination() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Worker> page = new PageImpl<>(List.of(worker), pageable, 1);

        when(workerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        WorkerSearchResponse response = workerSearchService.searchWorkers(
                "New York", null, categoryId, List.of(skillId), false,
                BigDecimal.valueOf(4.0), true, null, 3, 10,
                BigDecimal.valueOf(40), BigDecimal.valueOf(60), true,
                "rating", "desc", pageable
        );

        assertEquals(1, response.results().size());
        assertEquals(workerId, response.results().getFirst().id());
        assertEquals("Bob Builder", response.results().getFirst().fullName());
        assertEquals(1, response.totalElements());
        assertEquals(0, response.page());
        assertEquals("New York", response.filters().city());
        verify(workerRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchWorkersShouldFilterByAvailableOnDate() {
        LocalDate availableOn = LocalDate.now().plusDays(3);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Worker> page = new PageImpl<>(List.of(worker), pageable, 1);

        when(workerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(availabilityService.isAvailableOnDate(workerId, availableOn)).thenReturn(true);

        WorkerSearchResponse response = workerSearchService.searchWorkers(
                null, null, null, null, false, null, null, availableOn,
                null, null, null, null, true, "createdAt", "desc", pageable
        );

        assertEquals(1, response.results().size());
        verify(availabilityService).isAvailableOnDate(workerId, availableOn);
    }

    @Test
    void searchWorkersShouldExcludeWorkersUnavailableOnDate() {
        LocalDate availableOn = LocalDate.now().plusDays(3);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Worker> page = new PageImpl<>(List.of(worker), pageable, 1);

        when(workerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(availabilityService.isAvailableOnDate(workerId, availableOn)).thenReturn(false);

        WorkerSearchResponse response = workerSearchService.searchWorkers(
                null, null, null, null, false, null, null, availableOn,
                null, null, null, null, true, "createdAt", "desc", pageable
        );

        assertTrue(response.results().isEmpty());
        verify(availabilityService).isAvailableOnDate(workerId, availableOn);
    }

    @Test
    void searchWorkersShouldReturnEmptyResultsWhenNoWorkersMatch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Worker> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(workerRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        WorkerSearchResponse response = workerSearchService.searchWorkers(
                "Unknown City", null, null, null, false, null, null, null,
                null, null, null, null, true, "createdAt", "desc", pageable
        );

        assertTrue(response.results().isEmpty());
        assertEquals(0, response.totalElements());
        verify(workerRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void searchWorkersShouldCapPageSizeAtFifty() {
        Pageable largePageable = PageRequest.of(0, 100);
        Page<Worker> page = new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);

        when(workerRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 50, org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"
        ))))).thenReturn(page);

        WorkerSearchResponse response = workerSearchService.searchWorkers(
                null, null, null, null, false, null, null, null,
                null, null, null, null, true, "createdAt", "desc", largePageable
        );

        assertEquals(50, response.size());
    }
}
