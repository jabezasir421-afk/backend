package com.bluecollar.availability.repository;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.availability.entity.WorkerWorkingHours;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkerWorkingHoursRepositoryTest {

    @Autowired
    private WorkerWorkingHoursRepository workingHoursRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private Worker worker;

    @BeforeEach
    void setUp() {
        workingHoursRepository.deleteAll();
        workerRepository.deleteAll();
        userAccountRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing-AvailabilityRepo")
                .description("Pipe services")
                .active(true)
                .build());

        UserAccount userAccount = userAccountRepository.saveAndFlush(UserAccount.builder()
                .email("worker@example.com")
                .phoneNumber("+1234567890")
                .passwordHash("password")
                .role(UserRole.WORKER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build());

        worker = workerRepository.saveAndFlush(Worker.builder()
                .userAccount(userAccount)
                .firstName("Bob")
                .lastName("Builder")
                .phoneNumber("+0987654321")
                .email("bob@example.com")
                .category(category)
                .hourlyRate(BigDecimal.valueOf(50.00))
                .active(true)
                .verified(true)
                .available(true)
                .build());
    }

    @Test
    void saveShouldPersistWorkingHours() {
        WorkerWorkingHours workingHours = WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build();

        WorkerWorkingHours saved = workingHoursRepository.saveAndFlush(workingHours);

        assertTrue(saved.getId() != null);
        assertEquals((short) 1, saved.getDayOfWeek());
        assertEquals(LocalTime.of(9, 0), saved.getStartTime());
        assertTrue(saved.getActive());
    }

    @Test
    void findByWorkerIdAndActiveTrueOrderByDayOfWeekAscShouldReturnHoursInOrder() {
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 3)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(18, 0))
                .build());
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build());

        List<WorkerWorkingHours> hours = workingHoursRepository
                .findByWorkerIdAndActiveTrueOrderByDayOfWeekAsc(worker.getId());

        assertEquals(2, hours.size());
        assertEquals((short) 1, hours.getFirst().getDayOfWeek());
        assertEquals((short) 3, hours.get(1).getDayOfWeek());
    }

    @Test
    void findByWorkerIdAndDayOfWeekAndActiveTrueShouldReturnMatchingDay() {
        workingHoursRepository.saveAndFlush(WorkerWorkingHours.builder()
                .worker(worker)
                .dayOfWeek((short) 5)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(16, 0))
                .build());

        Optional<WorkerWorkingHours> found = workingHoursRepository
                .findByWorkerIdAndDayOfWeekAndActiveTrue(worker.getId(), (short) 5);

        assertTrue(found.isPresent());
        assertEquals(LocalTime.of(8, 0), found.get().getStartTime());
    }
}
