package com.bluecollar.booking.mapper;

import com.bluecollar.booking.dto.BookingResponse;
import com.bluecollar.booking.entity.Booking;
import com.bluecollar.customer.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final CustomerMapper customerMapper;

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getScheduledDate(),
                booking.getTimeSlot(),
                booking.getDescription(),
                booking.getQuotedAmount(),
                booking.getFinalAmount(),
                booking.getCancellationReason(),
                booking.getAcceptedAt(),
                booking.getCompletedAt(),
                booking.getCancelledAt(),
                booking.getCategory().getId(),
                booking.getCategory().getName(),
                booking.getWorker().getId(),
                booking.getWorker().getFirstName() + " " + booking.getWorker().getLastName(),
                customerMapper.toSummaryResponse(booking.getCustomer()),
                booking.getAddressLine1(),
                booking.getAddressCity(),
                booking.getAddressState(),
                booking.getAddressPincode(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}
