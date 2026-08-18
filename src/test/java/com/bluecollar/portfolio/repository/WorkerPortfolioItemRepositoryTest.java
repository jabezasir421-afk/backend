package com.bluecollar.portfolio.repository;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.portfolio.entity.WorkerPortfolioItem;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WorkerPortfolioItemRepositoryTest {

    @Autowired
    private WorkerPortfolioItemRepository portfolioItemRepository;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private Worker worker;

    @BeforeEach
    void setUp() {
        portfolioItemRepository.deleteAll();
        workerRepository.deleteAll();
        userAccountRepository.deleteAll();
        categoryRepository.deleteAll();

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .name("Plumbing-PortfolioRepo")
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
    void saveShouldPersistPortfolioItem() {
        UUID fileId = UUID.randomUUID();
        WorkerPortfolioItem item = WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(fileId)
                .title("Kitchen remodel")
                .description("Before and after")
                .displayOrder((short) 1)
                .build();

        WorkerPortfolioItem saved = portfolioItemRepository.saveAndFlush(item);

        assertTrue(saved.getId() != null);
        assertEquals("Kitchen remodel", saved.getTitle());
        assertEquals(fileId, saved.getFileId());
        assertTrue(saved.getActive());
    }

    @Test
    void findByWorkerIdAndActiveTrueOrderByDisplayOrderAscShouldReturnActiveItemsInOrder() {
        UUID fileIdOne = UUID.randomUUID();
        UUID fileIdTwo = UUID.randomUUID();

        portfolioItemRepository.saveAndFlush(WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(fileIdOne)
                .title("Second")
                .displayOrder((short) 2)
                .build());
        portfolioItemRepository.saveAndFlush(WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(fileIdTwo)
                .title("First")
                .displayOrder((short) 1)
                .build());

        List<WorkerPortfolioItem> items = portfolioItemRepository
                .findByWorkerIdAndActiveTrueOrderByDisplayOrderAsc(worker.getId());

        assertEquals(2, items.size());
        assertEquals("First", items.getFirst().getTitle());
        assertEquals("Second", items.get(1).getTitle());
    }

    @Test
    void findByIdAndWorkerIdAndActiveTrueShouldReturnItemWhenItExists() {
        UUID fileId = UUID.randomUUID();
        WorkerPortfolioItem saved = portfolioItemRepository.saveAndFlush(WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(fileId)
                .title("Bathroom")
                .displayOrder((short) 0)
                .build());

        Optional<WorkerPortfolioItem> found = portfolioItemRepository
                .findByIdAndWorkerIdAndActiveTrue(saved.getId(), worker.getId());

        assertTrue(found.isPresent());
        assertEquals("Bathroom", found.get().getTitle());
    }

    @Test
    void countByWorkerIdAndActiveTrueShouldReturnActiveItemCount() {
        portfolioItemRepository.saveAndFlush(WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(UUID.randomUUID())
                .title("Active item")
                .build());
        WorkerPortfolioItem inactive = WorkerPortfolioItem.builder()
                .worker(worker)
                .fileId(UUID.randomUUID())
                .title("Inactive item")
                .active(false)
                .build();
        portfolioItemRepository.saveAndFlush(inactive);

        long count = portfolioItemRepository.countByWorkerIdAndActiveTrue(worker.getId());

        assertEquals(1, count);
    }
}
