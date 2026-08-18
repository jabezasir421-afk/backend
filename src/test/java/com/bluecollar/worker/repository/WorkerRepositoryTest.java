package com.bluecollar.worker.repository;

import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.worker.entity.Gender;
import com.bluecollar.worker.entity.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkerRepositoryTest {

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        workerRepository.deleteAll();
        category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing")
                .description("Pipe services")
                .active(true)
                .build());
    }

    @Test
    void existsByPhoneNumberShouldReturnTrueWhenPhoneNumberExists() {
        workerRepository.saveAndFlush(buildWorker("+1234567890", "worker@example.com"));

        boolean exists = workerRepository.existsByPhoneNumber("+1234567890");

        assertTrue(exists);
    }

    @Test
    void existsByPhoneNumberShouldReturnFalseWhenPhoneNumberDoesNotExist() {
        boolean exists = workerRepository.existsByPhoneNumber("+9999999999");

        assertFalse(exists);
    }

    @Test
    void findByPhoneNumberShouldReturnWorkerWhenPhoneNumberMatches() {
        workerRepository.saveAndFlush(buildWorker("+1234567890", "worker@example.com"));

        Optional<Worker> found = workerRepository.findByPhoneNumber("+1234567890");

        assertTrue(found.isPresent());
        assertEquals("+1234567890", found.get().getPhoneNumber());
    }

    @Test
    void findByPhoneNumberShouldReturnEmptyWhenPhoneNumberDoesNotMatch() {
        Optional<Worker> found = workerRepository.findByPhoneNumber("+0000000000");

        assertFalse(found.isPresent());
    }

    @Test
    void existsByEmailIgnoreCaseShouldReturnTrueWhenEmailExistsIgnoringCase() {
        workerRepository.saveAndFlush(buildWorker("+1234567890", "Worker@Example.com"));

        boolean exists = workerRepository.existsByEmailIgnoreCase("worker@example.com");

        assertTrue(exists);
    }

    @Test
    void existsByEmailIgnoreCaseShouldReturnFalseWhenEmailDoesNotExist() {
        boolean exists = workerRepository.existsByEmailIgnoreCase("notfound@example.com");

        assertFalse(exists);
    }

    @Test
    void findByEmailIgnoreCaseShouldReturnWorkerWhenEmailMatchesIgnoringCase() {
        workerRepository.saveAndFlush(buildWorker("+1234567890", "Worker@Example.com"));

        Optional<Worker> found = workerRepository.findByEmailIgnoreCase("WORKER@EXAMPLE.COM");

        assertTrue(found.isPresent());
        assertEquals("Worker@Example.com", found.get().getEmail());
    }

    @Test
    void findByEmailIgnoreCaseShouldReturnEmptyWhenEmailDoesNotMatch() {
        Optional<Worker> found = workerRepository.findByEmailIgnoreCase("notfound@example.com");

        assertFalse(found.isPresent());
    }

    @Test
    void countByActiveTrueShouldReturnCountOfActiveWorkers() {
        Worker activeWorker = buildWorker("+1111111111", "active@example.com");
        activeWorker.setActive(true);
        Worker inactiveWorker = buildWorker("+2222222222", "inactive@example.com");
        inactiveWorker.setActive(false);

        workerRepository.saveAndFlush(activeWorker);
        workerRepository.saveAndFlush(inactiveWorker);

        long count = workerRepository.countByActiveTrue();

        assertEquals(1, count);
    }

    @Test
    void countByVerifiedTrueShouldReturnCountOfVerifiedWorkers() {
        Worker verifiedWorker = buildWorker("+1111111111", "verified@example.com");
        verifiedWorker.setVerified(true);
        Worker unverifiedWorker = buildWorker("+2222222222", "unverified@example.com");
        unverifiedWorker.setVerified(false);

        workerRepository.saveAndFlush(verifiedWorker);
        workerRepository.saveAndFlush(unverifiedWorker);

        long count = workerRepository.countByVerifiedTrue();

        assertEquals(1, count);
    }

    private Worker buildWorker(String phoneNumber, String email) {
        return Worker.builder()
                .firstName("John")
                .lastName("Doe")
                .phoneNumber(phoneNumber)
                .email(email)
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Experienced plumber")
                .hourlyRate(BigDecimal.valueOf(50))
                .category(category)
                .build();
    }
}
