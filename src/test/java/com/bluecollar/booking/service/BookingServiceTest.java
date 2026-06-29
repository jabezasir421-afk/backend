package com.bluecollar.booking.service;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.service.AddressServiceImpl;
import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.entity.UserRole;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.availability.service.AvailabilityService;
import com.bluecollar.booking.dto.*;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.booking.entity.BookingStatus;
import com.bluecollar.booking.exception.BookingNotFoundException;
import com.bluecollar.booking.exception.InvalidBookingStateException;
import com.bluecollar.booking.mapper.BookingMapper;
import com.bluecollar.booking.repository.BookingRepository;
import com.bluecollar.category.entity.Category;
import com.bluecollar.category.exception.CategoryNotFoundException;
import com.bluecollar.category.repository.CategoryRepository;
import com.bluecollar.common.event.BookingCreatedEvent;
import com.bluecollar.common.event.BookingStatusChangedEvent;
import com.bluecollar.common.exception.UnauthorizedException;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.service.CustomerServiceImpl;
import com.bluecollar.worker.entity.Worker;
import com.bluecollar.worker.exception.WorkerNotFoundException;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CustomerServiceImpl customerService;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private AddressServiceImpl addressService;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private AvailabilityService availabilityService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private UUID customerId;
    private UUID workerId;
    private UUID categoryId;
    private UUID addressId;
    private UUID bookingId;
    private UUID customerUserId;
    private UUID workerUserId;

    private Customer customer;
    private Worker worker;
    private Category category;
    private Address address;
    private Booking booking;
    private BookingResponse bookingResponse;
    private UserAccount customerUserAccount;
    private UserAccount workerUserAccount;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        workerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        addressId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        customerUserId = UUID.randomUUID();
        workerUserId = UUID.randomUUID();

        customerUserAccount = new UserAccount();
        customerUserAccount.setId(customerUserId);

        workerUserAccount = new UserAccount();
        workerUserAccount.setId(workerUserId);

        customer = new Customer();
        customer.setId(customerId);
        customer.setUserAccount(customerUserAccount);

        category = new Category();
        category.setId(categoryId);
        category.setName("Plumbing");

        worker = Worker.builder()
                .firstName("Bob")
                .lastName("Builder")
                .hourlyRate(BigDecimal.valueOf(50.00))
                .active(true)
                .verified(true)
                .available(true)
                .category(category)
                .build();
        worker.setId(workerId);
        worker.setUserAccount(workerUserAccount);

        address = Address.builder()
                .line1("123 Main St")
                .city("New York")
                .state("NY")
                .pincode("10001")
                .build();
        address.setId(addressId);

        booking = Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.PENDING)
                .scheduledDate(LocalDate.now().plusDays(3))
                .timeSlot("10:00-12:00")
                .description("Fix leak")
                .quotedAmount(BigDecimal.valueOf(100.00))
                .addressLine1("123 Main St")
                .addressCity("New York")
                .addressState("NY")
                .addressPincode("10001")
                .build();
        booking.setId(bookingId);

        bookingResponse = new BookingResponse(
                bookingId, BookingStatus.PENDING,
                LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak",
                BigDecimal.valueOf(100.00), null, null, null, null, null,
                categoryId, "Plumbing", workerId, "Bob Builder", null,
                "123 Main St", "New York", "NY", "10001",
                Instant.now(), Instant.now()
        );

        securityUtilsMock = Mockito.mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ─── createBooking ─────────────────────────────────────────────────────────

    @Test
    void createBookingShouldCreateBookingWhenValid() {
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(addressService.findActiveAddressForCustomer(addressId, customerId)).thenReturn(address);
        when(availabilityService.isAvailableOnDate(workerId, request.scheduledDate())).thenReturn(true);
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        BookingResponse response = bookingService.createBooking(request);

        assertNotNull(response);
        assertEquals(bookingResponse, response);
        verify(eventPublisher).publishEvent(any(BookingCreatedEvent.class));
    }

    @Test
    void createBookingShouldThrowWhenWorkerNotFound() {
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.empty());

        assertThrows(WorkerNotFoundException.class, () -> bookingService.createBooking(request));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingShouldThrowWhenCategoryNotFound() {
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBookingShouldThrowWhenWorkerNotActive() {
        worker.setActive(false);
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidBookingStateException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBookingShouldThrowWhenWorkerNotVerified() {
        worker.setVerified(false);
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidBookingStateException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBookingShouldThrowWhenWorkerCategoryMismatch() {
        Category otherCategory = new Category();
        otherCategory.setId(UUID.randomUUID());

        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(otherCategory));
        when(addressService.findActiveAddressForCustomer(addressId, customerId)).thenReturn(address);

        assertThrows(InvalidBookingStateException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBookingShouldThrowWhenWorkerHourlyRateNotConfigured() {
        worker.setHourlyRate(null);
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(addressService.findActiveAddressForCustomer(addressId, customerId)).thenReturn(address);

        assertThrows(InvalidBookingStateException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBookingShouldThrowWhenWorkerNotAvailableOnDate() {
        CreateBookingRequest request = new CreateBookingRequest(
                workerId, categoryId, addressId, LocalDate.now().plusDays(3), "10:00-12:00", "Fix leak");

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(workerRepository.findById(workerId)).thenReturn(Optional.of(worker));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(addressService.findActiveAddressForCustomer(addressId, customerId)).thenReturn(address);
        when(availabilityService.isAvailableOnDate(workerId, request.scheduledDate())).thenReturn(false);

        assertThrows(InvalidBookingStateException.class, () -> bookingService.createBooking(request));
    }

    // ─── getMyBookings ─────────────────────────────────────────────────────────

    @Test
    void getMyBookingsShouldReturnCustomerBookingsWhenRoleIsCustomer() {
        AuthenticatedUser custUser = new AuthenticatedUser(customerUserId, "cust", UserRole.CUSTOMER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(custUser);
        Pageable pageable = PageRequest.of(0, 10);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findByCustomerId(customerId, pageable))
                .thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        Page<BookingResponse> result = bookingService.getMyBookings(null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMyBookingsShouldReturnWorkerBookingsWhenRoleIsWorker() {
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "wrk", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);
        Pageable pageable = PageRequest.of(0, 10);

        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.findByWorkerId(workerId, pageable))
                .thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        Page<BookingResponse> result = bookingService.getMyBookings(null, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMyBookingsShouldFilterByStatusForCustomer() {
        AuthenticatedUser custUser = new AuthenticatedUser(customerUserId, "cust", UserRole.CUSTOMER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(custUser);
        Pageable pageable = PageRequest.of(0, 10);

        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(bookingRepository.findByCustomerIdAndStatus(customerId, BookingStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        Page<BookingResponse> result = bookingService.getMyBookings(BookingStatus.PENDING, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMyBookingsShouldThrowForAdminRole() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin", UserRole.ADMIN);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(admin);

        assertThrows(UnauthorizedException.class,
                () -> bookingService.getMyBookings(null, PageRequest.of(0, 10)));
    }

    // ─── getBookingById ────────────────────────────────────────────────────────

    @Test
    void getBookingByIdShouldReturnBookingWhenAdminViews() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin", UserRole.ADMIN);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(admin);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        BookingResponse response = bookingService.getBookingById(bookingId);

        assertNotNull(response);
    }

    @Test
    void getBookingByIdShouldThrowWhenBookingNotFound() {
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(BookingNotFoundException.class, () -> bookingService.getBookingById(bookingId));
    }

    @Test
    void getBookingByIdShouldThrowWhenCustomerViewsOtherBooking() {
        AuthenticatedUser custUser = new AuthenticatedUser(customerUserId, "cust", UserRole.CUSTOMER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(custUser);

        Customer otherCustomer = new Customer();
        otherCustomer.setId(UUID.randomUUID());
        booking.setCustomer(otherCustomer);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);

        assertThrows(UnauthorizedException.class, () -> bookingService.getBookingById(bookingId));
    }

    // ─── acceptBooking ─────────────────────────────────────────────────────────

    @Test
    void acceptBookingShouldTransitionStatusToAccepted() {
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        bookingService.acceptBooking(bookingId);

        assertEquals(BookingStatus.ACCEPTED, booking.getStatus());
        assertNotNull(booking.getAcceptedAt());
        verify(eventPublisher).publishEvent(any(BookingStatusChangedEvent.class));
    }

    @Test
    void acceptBookingShouldThrowWhenUserNotAssignedWorker() {
        UUID otherWorkerUserId = UUID.randomUUID();
        AuthenticatedUser wrkUser = new AuthenticatedUser(otherWorkerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        Worker otherWorker = new Worker();
        otherWorker.setId(UUID.randomUUID());

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(otherWorkerUserId)).thenReturn(Optional.of(otherWorker));

        assertThrows(UnauthorizedException.class, () -> bookingService.acceptBooking(bookingId));
    }

    @Test
    void acceptBookingShouldThrowWhenInvalidTransition() {
        booking.setStatus(BookingStatus.COMPLETED);
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidBookingStateException.class, () -> bookingService.acceptBooking(bookingId));
    }

    // ─── startBooking ──────────────────────────────────────────────────────────

    @Test
    void startBookingShouldTransitionToInProgress() {
        booking.setStatus(BookingStatus.ACCEPTED);
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        bookingService.startBooking(bookingId);

        assertEquals(BookingStatus.IN_PROGRESS, booking.getStatus());
        verify(eventPublisher).publishEvent(any(BookingStatusChangedEvent.class));
    }

    @Test
    void startBookingShouldThrowWhenInvalidTransition() {
        booking.setStatus(BookingStatus.PENDING);
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidBookingStateException.class, () -> bookingService.startBooking(bookingId));
    }

    // ─── rejectBooking ─────────────────────────────────────────────────────────

    @Test
    void rejectBookingShouldTransitionToRejected() {
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        RejectBookingRequest request = new RejectBookingRequest("Not available");
        bookingService.rejectBooking(bookingId, request);

        assertEquals(BookingStatus.REJECTED, booking.getStatus());
        assertEquals("Not available", booking.getCancellationReason());
        assertNotNull(booking.getCancelledAt());
    }

    @Test
    void rejectBookingShouldHandleNullReason() {
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        RejectBookingRequest request = new RejectBookingRequest(null);
        bookingService.rejectBooking(bookingId, request);

        assertEquals(BookingStatus.REJECTED, booking.getStatus());
    }

    // ─── cancelBooking ─────────────────────────────────────────────────────────

    @Test
    void cancelBookingShouldCancelPendingBookingForOwningCustomer() {
        AuthenticatedUser custUser = new AuthenticatedUser(customerUserId, "cust", UserRole.CUSTOMER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(custUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);
        when(userAccountRepository.findById(customerUserId)).thenReturn(Optional.of(customerUserAccount));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        CancelBookingRequest request = new CancelBookingRequest("Changed mind");
        bookingService.cancelBooking(bookingId, request);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals("Changed mind", booking.getCancellationReason());
    }

    @Test
    void cancelBookingShouldThrowWhenCustomerDoesNotOwnBooking() {
        AuthenticatedUser custUser = new AuthenticatedUser(customerUserId, "cust", UserRole.CUSTOMER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(custUser);

        Customer otherCustomer = new Customer();
        otherCustomer.setId(UUID.randomUUID());
        booking.setCustomer(otherCustomer);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(customerService.findCustomerByCurrentUser()).thenReturn(customer);

        assertThrows(UnauthorizedException.class,
                () -> bookingService.cancelBooking(bookingId, new CancelBookingRequest("reason")));
    }

    @Test
    void cancelBookingShouldAllowAdminToCancelAnyBooking() {
        UUID adminUserId = UUID.randomUUID();
        UserAccount adminAccount = new UserAccount();
        adminAccount.setId(adminUserId);

        AuthenticatedUser admin = new AuthenticatedUser(adminUserId, "admin", UserRole.ADMIN);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(admin);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(userAccountRepository.findById(adminUserId)).thenReturn(Optional.of(adminAccount));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        bookingService.cancelBooking(bookingId, new CancelBookingRequest("Admin override"));

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    // ─── completeBooking ───────────────────────────────────────────────────────

    @Test
    void completeBookingShouldTransitionToCompleted() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        CompleteBookingRequest request = new CompleteBookingRequest(BigDecimal.valueOf(120.00));
        bookingService.completeBooking(bookingId, request);

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(BigDecimal.valueOf(120.00), booking.getFinalAmount());
        assertNotNull(booking.getCompletedAt());
    }

    @Test
    void completeBookingShouldUsedQuotedAmountWhenFinalAmountIsNull() {
        booking.setStatus(BookingStatus.IN_PROGRESS);
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));
        when(bookingRepository.save(booking)).thenReturn(booking);
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        CompleteBookingRequest request = new CompleteBookingRequest(null);
        bookingService.completeBooking(bookingId, request);

        assertEquals(booking.getQuotedAmount(), booking.getFinalAmount());
    }

    @Test
    void completeBookingShouldThrowWhenInvalidTransition() {
        booking.setStatus(BookingStatus.PENDING);
        AuthenticatedUser wrkUser = new AuthenticatedUser(workerUserId, "worker", UserRole.WORKER);
        securityUtilsMock.when(SecurityUtils::getCurrentUser).thenReturn(wrkUser);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(workerRepository.findByUserAccountId(workerUserId)).thenReturn(Optional.of(worker));

        assertThrows(InvalidBookingStateException.class,
                () -> bookingService.completeBooking(bookingId, new CompleteBookingRequest(null)));
    }

    // ─── getAllBookings ────────────────────────────────────────────────────────

    @Test
    void getAllBookingsShouldDelegateToRepositoryWithFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        when(bookingRepository.findAllWithFilters(
                eq(BookingStatus.PENDING), eq(categoryId), eq(workerId), eq(customerId),
                any(), any(), eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(booking)));
        when(bookingMapper.toResponse(booking)).thenReturn(bookingResponse);

        Page<BookingResponse> result = bookingService.getAllBookings(
                BookingStatus.PENDING, categoryId, workerId, customerId, null, null, pageable);

        assertEquals(1, result.getTotalElements());
    }
}
