package com.bluecollar.address.repository;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.entity.AddressType;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.repository.CustomerRepository;
import com.bluecollar.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.test.database.replace=NONE")
@ActiveProfiles("test")
@Transactional
class AddressRepositoryTest {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        addressRepository.deleteAll();
        customerRepository.deleteAll();
        userAccountRepository.deleteAll();

        UserAccount userAccount = UserAccount.builder()
                .email("jane.doe@example.com")
                .phoneNumber("+919876543219")
                .passwordHash("hashed_pass")
                .role(UserRole.CUSTOMER)
                .active(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
        UserAccount savedUser = userAccountRepository.saveAndFlush(userAccount);

        Customer customer = Customer.builder()
                .userAccount(savedUser)
                .firstName("Jane")
                .lastName("Doe")
                .active(true)
                .build();
        savedCustomer = customerRepository.saveAndFlush(customer);
    }

    // ─── findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc ─────────

    @Test
    void findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDescShouldReturnOrderedActiveAddressesOnly() throws InterruptedException {
        // Arrange
        Address address1 = buildAddress("Home", AddressType.HOME, "Line 1 A", "400001", false);
        addressRepository.saveAndFlush(address1);
        Thread.sleep(10); // Ensure different timestamps

        Address address2 = buildAddress("Work", AddressType.WORK, "Line 1 B", "400002", true);
        addressRepository.saveAndFlush(address2);
        Thread.sleep(10);

        Address address3 = buildAddress("Other", AddressType.OTHER, "Line 1 C", "400003", false);
        addressRepository.saveAndFlush(address3);

        Address inactiveAddress = buildAddress("Old Home", AddressType.HOME, "Line 1 D", "400004", false);
        inactiveAddress.setActive(false);
        addressRepository.saveAndFlush(inactiveAddress);

        // Act
        List<Address> results = addressRepository.findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(savedCustomer.getId());

        // Assert
        assertEquals(3, results.size());
        // Default first
        assertEquals(address2.getId(), results.get(0).getId());
        // Then by createdAt desc (address3 created after address1)
        assertEquals(address3.getId(), results.get(1).getId());
        assertEquals(address1.getId(), results.get(2).getId());
    }

    // ─── countByCustomerIdAndActiveTrue ──────────────────────────────────────

    @Test
    void countByCustomerIdAndActiveTrueShouldCountOnlyActiveAddresses() {
        // Arrange
        Address active1 = buildAddress("Home", AddressType.HOME, "L1", "400001", true);
        Address active2 = buildAddress("Work", AddressType.WORK, "L2", "400002", false);
        Address inactive = buildAddress("Inactive", AddressType.OTHER, "L3", "400003", false);
        inactive.setActive(false);

        addressRepository.saveAllAndFlush(List.of(active1, active2, inactive));

        // Act
        long count = addressRepository.countByCustomerIdAndActiveTrue(savedCustomer.getId());

        // Assert
        assertEquals(2, count);
    }

    // ─── findByIdAndCustomerId ───────────────────────────────────────────────

    @Test
    void findByIdAndCustomerIdShouldReturnAddressWhenMatch() {
        // Arrange
        Address address = buildAddress("Home", AddressType.HOME, "Line 1", "400001", true);
        Address saved = addressRepository.saveAndFlush(address);

        // Act
        Optional<Address> result = addressRepository.findByIdAndCustomerId(saved.getId(), savedCustomer.getId());

        // Assert
        assertTrue(result.isPresent());
        assertEquals(saved.getId(), result.get().getId());
    }

    @Test
    void findByIdAndCustomerIdShouldReturnEmptyWhenCustomerIdMismatch() {
        // Arrange
        Address address = buildAddress("Home", AddressType.HOME, "Line 1", "400001", true);
        Address saved = addressRepository.saveAndFlush(address);

        // Act
        Optional<Address> result = addressRepository.findByIdAndCustomerId(saved.getId(), UUID.randomUUID());

        // Assert
        assertFalse(result.isPresent());
    }

    // ─── findByCustomerIdAndIsDefaultTrueAndActiveTrue ───────────────────────

    @Test
    void findByCustomerIdAndIsDefaultTrueAndActiveTrueShouldReturnDefaultsOnly() {
        // Arrange
        Address defaultActive = buildAddress("Home", AddressType.HOME, "Line 1", "400001", true);
        Address nonDefaultActive = buildAddress("Work", AddressType.WORK, "Line 2", "400002", false);
        Address defaultInactive = buildAddress("Old Home", AddressType.HOME, "Line 3", "400003", true);
        defaultInactive.setActive(false);

        addressRepository.saveAllAndFlush(List.of(defaultActive, nonDefaultActive, defaultInactive));

        // Act
        List<Address> results = addressRepository.findByCustomerIdAndIsDefaultTrueAndActiveTrue(savedCustomer.getId());

        // Assert
        assertEquals(1, results.size());
        assertEquals(defaultActive.getId(), results.get(0).getId());
    }

    // ─── PAGINATION AND SORTING ──────────────────────────────────────────────

    @Test
    void findAllShouldSupportPaginationAndSorting() {
        // Arrange
        Address addr1 = buildAddress("Home", AddressType.HOME, "L1", "400001", false);
        Address addr2 = buildAddress("Office", AddressType.WORK, "L2", "400002", false);
        Address addr3 = buildAddress("Apartment", AddressType.OTHER, "L3", "400003", false);
        addressRepository.saveAllAndFlush(List.of(addr1, addr2, addr3));

        // Act
        PageRequest pageRequest = PageRequest.of(0, 2, Sort.by("label").ascending());
        Page<Address> page = addressRepository.findAll(pageRequest);

        // Assert
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        // Ordered: Apartment -> Home -> Office. First page should have Apartment and Home.
        assertEquals("Apartment", page.getContent().get(0).getLabel());
        assertEquals("Home", page.getContent().get(1).getLabel());
    }

    // ─── CONSTRAINTS ─────────────────────────────────────────────────────────

    @Test
    void saveShouldThrowExceptionWhenRequiredFieldsAreNull() {
        // Arrange & Assert
        Address addressNullLabel = Address.builder()
                .customer(savedCustomer)
                .addressType(AddressType.HOME)
                .line1("Line 1")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .isDefault(false)
                .active(true)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> addressRepository.saveAndFlush(addressNullLabel));
    }

    @Test
    void saveShouldThrowExceptionWhenCustomerIsNull() {
        // Arrange & Assert
        Address addressNullCustomer = Address.builder()
                .label("Home")
                .addressType(AddressType.HOME)
                .line1("Line 1")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .isDefault(false)
                .active(true)
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> addressRepository.saveAndFlush(addressNullCustomer));
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private Address buildAddress(String label, AddressType type, String line1, String pincode, boolean isDefault) {
        return Address.builder()
                .customer(savedCustomer)
                .label(label)
                .addressType(type)
                .line1(line1)
                .city("Mumbai")
                .state("Maharashtra")
                .pincode(pincode)
                .isDefault(isDefault)
                .active(true)
                .build();
    }
}
