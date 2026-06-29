package com.bluecollar.booking.service;

import com.bluecollar.address.entity.Address;
import com.bluecollar.address.service.AddressServiceImpl;
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
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {

    private static final Duration CANCELLATION_CUTOFF = Duration.ofHours(2);

    private final BookingRepository bookingRepository;
    private final CustomerServiceImpl customerService;
    private final WorkerRepository workerRepository;
    private final CategoryRepository categoryRepository;
    private final AddressServiceImpl addressService;
    private final UserAccountRepository userAccountRepository;
    private final BookingMapper bookingMapper;
    private final AvailabilityService availabilityService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public BookingResponse createBooking(CreateBookingRequest request) {
        Customer customer = customerService.findCustomerByCurrentUser();
        Worker worker = findAvailableWorker(request.workerId());
        Category category = findCategory(request.categoryId());
        Address address = addressService.findActiveAddressForCustomer(request.addressId(), customer.getId());

        validateWorkerCategory(worker, category);
        validateWorkerHourlyRate(worker);
        if (!availabilityService.isAvailableOnDate(worker.getId(), request.scheduledDate())) {
            throw new InvalidBookingStateException("Worker is not available on the selected date");
        }

        Booking booking = Booking.builder()
                .customer(customer)
                .worker(worker)
                .category(category)
                .address(address)
                .status(BookingStatus.PENDING)
                .scheduledDate(request.scheduledDate())
                .timeSlot(request.timeSlot().trim())
                .description(request.description().trim())
                .quotedAmount(calculateQuotedAmount(worker.getHourlyRate(), request.timeSlot()))
                .addressLine1(address.getLine1())
                .addressCity(address.getCity())
                .addressState(address.getState())
                .addressPincode(address.getPincode())
                .build();

        Booking saved = bookingRepository.save(booking);
        publishBookingCreated(saved);
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(BookingStatus status, Pageable pageable) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();

        if (currentUser.role() == UserRole.CUSTOMER) {
            Customer customer = customerService.findCustomerByCurrentUser();
            Page<Booking> bookings = status == null
                    ? bookingRepository.findByCustomerId(customer.getId(), pageable)
                    : bookingRepository.findByCustomerIdAndStatus(customer.getId(), status, pageable);
            return bookings.map(bookingMapper::toResponse);
        }

        if (currentUser.role() == UserRole.WORKER) {
            Worker worker = findWorkerByCurrentUser();
            Page<Booking> bookings = status == null
                    ? bookingRepository.findByWorkerId(worker.getId(), pageable)
                    : bookingRepository.findByWorkerIdAndStatus(worker.getId(), status, pageable);
            return bookings.map(bookingMapper::toResponse);
        }

        throw new UnauthorizedException("Only customers and workers can access their bookings");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(
            BookingStatus status,
            UUID categoryId,
            UUID workerId,
            UUID customerId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable
    ) {
        return bookingRepository.findAllWithFilters(status, categoryId, workerId, customerId, fromDate, toDate, pageable)
                .map(bookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID id) {
        Booking booking = findBooking(id);
        assertCanViewBooking(booking);
        return bookingMapper.toResponse(booking);
    }

    @Override
    public BookingResponse acceptBooking(UUID id) {
        Booking booking = findBookingForWorker(id);
        BookingStatus oldStatus = booking.getStatus();
        transition(booking, BookingStatus.ACCEPTED);
        booking.setAcceptedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);
        publishStatusChanged(saved, oldStatus);
        return bookingMapper.toResponse(saved);
    }

    @Override
    public BookingResponse startBooking(UUID id) {
        Booking booking = findBookingForWorker(id);
        BookingStatus oldStatus = booking.getStatus();
        transition(booking, BookingStatus.IN_PROGRESS);
        Booking saved = bookingRepository.save(booking);
        publishStatusChanged(saved, oldStatus);
        return bookingMapper.toResponse(saved);
    }

    @Override
    public BookingResponse rejectBooking(UUID id, RejectBookingRequest request) {
        Booking booking = findBookingForWorker(id);
        BookingStatus oldStatus = booking.getStatus();
        transition(booking, BookingStatus.REJECTED);
        if (request.reason() != null && !request.reason().isBlank()) {
            booking.setCancellationReason(request.reason().trim());
        }
        booking.setCancelledAt(Instant.now());
        Booking saved = bookingRepository.save(booking);
        publishStatusChanged(saved, oldStatus);
        return bookingMapper.toResponse(saved);
    }

    @Override
    public BookingResponse cancelBooking(UUID id, CancelBookingRequest request) {
        Booking booking = findBooking(id);
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        assertCanCancelBooking(booking, currentUser);

        if (booking.getStatus() != BookingStatus.PENDING) {
            validateCancellationWindow(booking);
        }

        BookingStatus oldStatus = booking.getStatus();
        transition(booking, BookingStatus.CANCELLED);
        booking.setCancellationReason(request.reason().trim());
        booking.setCancelledAt(Instant.now());
        booking.setCancelledBy(userAccountRepository.findById(currentUser.userAccountId()).orElseThrow());

        Booking saved = bookingRepository.save(booking);
        publishStatusChanged(saved, oldStatus);
        return bookingMapper.toResponse(saved);
    }

    @Override
    public BookingResponse completeBooking(UUID id, CompleteBookingRequest request) {
        Booking booking = findBookingForWorker(id);
        BookingStatus oldStatus = booking.getStatus();
        transition(booking, BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        booking.setFinalAmount(request.finalAmount() != null ? request.finalAmount() : booking.getQuotedAmount());
        Booking saved = bookingRepository.save(booking);
        publishStatusChanged(saved, oldStatus);
        return bookingMapper.toResponse(saved);
    }

    private Booking findBooking(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    private Booking findBookingForWorker(UUID id) {
        Booking booking = findBooking(id);
        Worker worker = findWorkerByCurrentUser();
        if (!booking.getWorker().getId().equals(worker.getId())) {
            throw new UnauthorizedException("You are not assigned to this booking");
        }
        return booking;
    }

    private Worker findWorkerByCurrentUser() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return workerRepository.findByUserAccountId(currentUser.userAccountId())
                .orElseThrow(() -> new WorkerNotFoundException("Worker profile not found for current user"));
    }

    private Worker findAvailableWorker(UUID workerId) {
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new WorkerNotFoundException(workerId));

        if (!Boolean.TRUE.equals(worker.getActive())
                || !Boolean.TRUE.equals(worker.getVerified())
                || !Boolean.TRUE.equals(worker.getAvailable())) {
            throw new InvalidBookingStateException("Worker is not available for booking");
        }
        return worker;
    }

    private Category findCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private void validateWorkerCategory(Worker worker, Category category) {
        if (!worker.getCategory().getId().equals(category.getId())) {
            throw new InvalidBookingStateException("Worker does not belong to the selected category");
        }
    }

    private void validateWorkerHourlyRate(Worker worker) {
        if (worker.getHourlyRate() == null || worker.getHourlyRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBookingStateException("Worker hourly rate is not configured");
        }
    }

    private BigDecimal calculateQuotedAmount(BigDecimal hourlyRate, String timeSlot) {
        String[] parts = timeSlot.trim().split("-");
        LocalTime start = LocalTime.parse(parts[0]);
        LocalTime end = LocalTime.parse(parts[1]);
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes <= 0) {
            throw new InvalidBookingStateException("Time slot end must be after start");
        }
        BigDecimal hours = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return hourlyRate.multiply(hours);
    }

    private void transition(Booking booking, BookingStatus target) {
        if (!booking.getStatus().canTransitionTo(target)) {
            throw new InvalidBookingStateException(booking.getStatus(), target);
        }
        booking.setStatus(target);
    }

    private void assertCanViewBooking(Booking booking) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }
        if (currentUser.role() == UserRole.CUSTOMER) {
            Customer customer = customerService.findCustomerByCurrentUser();
            if (!booking.getCustomer().getId().equals(customer.getId())) {
                throw new UnauthorizedException("You cannot view this booking");
            }
            return;
        }
        if (currentUser.role() == UserRole.WORKER) {
            Worker worker = findWorkerByCurrentUser();
            if (!booking.getWorker().getId().equals(worker.getId())) {
                throw new UnauthorizedException("You cannot view this booking");
            }
            return;
        }
        throw new UnauthorizedException("You cannot view this booking");
    }

    private void assertCanCancelBooking(Booking booking, AuthenticatedUser currentUser) {
        if (currentUser.role() == UserRole.ADMIN) {
            return;
        }
        if (currentUser.role() == UserRole.CUSTOMER) {
            Customer customer = customerService.findCustomerByCurrentUser();
            if (!booking.getCustomer().getId().equals(customer.getId())) {
                throw new UnauthorizedException("You cannot cancel this booking");
            }
            return;
        }
        if (currentUser.role() == UserRole.WORKER) {
            Worker worker = findWorkerByCurrentUser();
            if (!booking.getWorker().getId().equals(worker.getId())) {
                throw new UnauthorizedException("You cannot cancel this booking");
            }
            return;
        }
        throw new UnauthorizedException("You cannot cancel this booking");
    }

    private void validateCancellationWindow(Booking booking) {
        LocalTime slotStart = LocalTime.parse(booking.getTimeSlot().split("-")[0]);
        LocalDateTime scheduledDateTime = LocalDateTime.of(booking.getScheduledDate(), slotStart);
        if (LocalDateTime.now().plus(CANCELLATION_CUTOFF).isAfter(scheduledDateTime)) {
            throw new InvalidBookingStateException("Bookings cannot be cancelled within 2 hours of the scheduled time");
        }
    }

    private void publishBookingCreated(Booking booking) {
        eventPublisher.publishEvent(new BookingCreatedEvent(
                booking.getId(),
                booking.getCustomer().getUserAccount().getId(),
                booking.getWorker().getUserAccount() != null ? booking.getWorker().getUserAccount().getId() : null
        ));
    }

    private void publishStatusChanged(Booking booking, BookingStatus oldStatus) {
        eventPublisher.publishEvent(new BookingStatusChangedEvent(
                booking.getId(),
                oldStatus,
                booking.getStatus(),
                booking.getCustomer().getUserAccount().getId(),
                booking.getWorker().getUserAccount() != null ? booking.getWorker().getUserAccount().getId() : null
        ));
    }
}
