package com.bluecollar.customer.repository;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.customer.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    private UserAccount savedUserAccount;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
        userAccountRepository.deleteAll();
        savedUserAccount = userAccountRepository.saveAndFlush(buildUserAccount("john.doe@example.com", "+919876543210"));
    }

    // ─── findByUserAccountId ──────────────────────────────────────────────────

    @Test
    void findByUserAccountIdShouldReturnCustomerWhenUserAccountExists() {
        Customer customer = customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "John", "Doe"));

        Optional<Customer> found = customerRepository.findByUserAccountId(savedUserAccount.getId());

        assertTrue(found.isPresent());
        assertEquals(customer.getId(), found.get().getId());
        assertEquals("John", found.get().getFirstName());
        assertEquals("Doe", found.get().getLastName());
    }

    @Test
    void findByUserAccountIdShouldReturnEmptyWhenNoCustomerLinkedToUserAccount() {
        UUID unknownUserAccountId = UUID.randomUUID();

        Optional<Customer> found = customerRepository.findByUserAccountId(unknownUserAccountId);

        assertFalse(found.isPresent());
    }

    @Test
    void findByUserAccountIdShouldReturnCorrectCustomerWhenMultipleExist() {
        UserAccount secondUserAccount = userAccountRepository.saveAndFlush(
                buildUserAccount("jane.smith@example.com", "+919876543211")
        );

        Customer first = customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "John", "Doe"));
        customerRepository.saveAndFlush(buildCustomer(secondUserAccount, "Jane", "Smith"));

        Optional<Customer> found = customerRepository.findByUserAccountId(savedUserAccount.getId());

        assertTrue(found.isPresent());
        assertEquals(first.getId(), found.get().getId());
        assertEquals("John", found.get().getFirstName());
    }

    // ─── countByActiveTrue ────────────────────────────────────────────────────

    @Test
    void countByActiveTrueShouldReturnZeroWhenNoActiveCustomersExist() {
        long count = customerRepository.countByActiveTrue();

        assertEquals(0, count);
    }

    @Test
    void countByActiveTrueShouldReturnOnlyActiveCustomerCount() {
        UserAccount secondUserAccount = userAccountRepository.saveAndFlush(
                buildUserAccount("inactive@example.com", "+919876543212")
        );

        customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "Active", "Customer"));

        Customer inactive = buildCustomer(secondUserAccount, "Inactive", "Customer");
        inactive.setActive(false);
        customerRepository.saveAndFlush(inactive);

        long count = customerRepository.countByActiveTrue();

        assertEquals(1, count);
    }

    @Test
    void countByActiveTrueShouldCountAllActiveCustomers() {
        UserAccount second = userAccountRepository.saveAndFlush(
                buildUserAccount("second@example.com", "+919876543213")
        );
        UserAccount third = userAccountRepository.saveAndFlush(
                buildUserAccount("third@example.com", "+919876543214")
        );

        customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "Alice", "A"));
        customerRepository.saveAndFlush(buildCustomer(second, "Bob", "B"));
        customerRepository.saveAndFlush(buildCustomer(third, "Charlie", "C"));

        long count = customerRepository.countByActiveTrue();

        assertEquals(3, count);
    }

    // ─── Pagination and Sorting ───────────────────────────────────────────────

    @Test
    void findAllShouldReturnPagedResults() {
        UserAccount second = userAccountRepository.saveAndFlush(
                buildUserAccount("second@example.com", "+919876543213")
        );
        UserAccount third = userAccountRepository.saveAndFlush(
                buildUserAccount("third@example.com", "+919876543214")
        );

        customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "Alice", "A"));
        customerRepository.saveAndFlush(buildCustomer(second, "Bob", "B"));
        customerRepository.saveAndFlush(buildCustomer(third, "Charlie", "C"));

        PageRequest pageable = PageRequest.of(0, 2);

        Page<Customer> page = customerRepository.findAll(pageable);

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void findAllShouldReturnSecondPageCorrectly() {
        UserAccount second = userAccountRepository.saveAndFlush(
                buildUserAccount("second@example.com", "+919876543213")
        );
        UserAccount third = userAccountRepository.saveAndFlush(
                buildUserAccount("third@example.com", "+919876543214")
        );

        customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "Alice", "A"));
        customerRepository.saveAndFlush(buildCustomer(second, "Bob", "B"));
        customerRepository.saveAndFlush(buildCustomer(third, "Charlie", "C"));

        PageRequest pageable = PageRequest.of(1, 2);
        Page<Customer> page = customerRepository.findAll(pageable);

        assertEquals(3, page.getTotalElements());
        assertEquals(1, page.getContent().size());
    }

    @Test
    void findAllShouldSortByFirstNameAscending() {
        UserAccount second = userAccountRepository.saveAndFlush(
                buildUserAccount("second@example.com", "+919876543213")
        );
        UserAccount third = userAccountRepository.saveAndFlush(
                buildUserAccount("third@example.com", "+919876543214")
        );

        customerRepository.saveAndFlush(buildCustomer(savedUserAccount, "Charlie", "C"));
        customerRepository.saveAndFlush(buildCustomer(second, "Alice", "A"));
        customerRepository.saveAndFlush(buildCustomer(third, "Bob", "B"));

        PageRequest pageable = PageRequest.of(0, 10, Sort.by("firstName").ascending());
        Page<Customer> page = customerRepository.findAll(pageable);

        List<String> firstNames = page.getContent().stream()
                .map(Customer::getFirstName)
                .toList();

        assertEquals(List.of("Alice", "Bob", "Charlie"), firstNames);
    }

    // ─── Constraints ─────────────────────────────────────────────────────────

    @Test
    void saveShouldPersistCustomerWithActiveDefaultTrue() {
        Customer customer = Customer.builder()
                .userAccount(savedUserAccount)
                .firstName("John")
                .lastName("Doe")
                .build();

        Customer saved = customerRepository.saveAndFlush(customer);

        assertTrue(saved.getActive());
    }

    @Test
    void saveShouldGenerateUuidForNewCustomer() {
        Customer customer = buildCustomer(savedUserAccount, "John", "Doe");

        Customer saved = customerRepository.saveAndFlush(customer);

        assertTrue(saved.getId() != null);
    }

    @Test
    void saveShouldPersistCreatedAtAndUpdatedAt() {
        Customer customer = buildCustomer(savedUserAccount, "John", "Doe");

        Customer saved = customerRepository.saveAndFlush(customer);

        assertTrue(saved.getCreatedAt() != null);
        assertTrue(saved.getUpdatedAt() != null);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private UserAccount buildUserAccount(String email, String phone) {
        return UserAccount.builder()
                .email(email)
                .phoneNumber(phone)
                .passwordHash("hashed_password")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }

    private Customer buildCustomer(UserAccount userAccount, String firstName, String lastName) {
        return Customer.builder()
                .userAccount(userAccount)
                .firstName(firstName)
                .lastName(lastName)
                .active(true)
                .build();
    }
}
