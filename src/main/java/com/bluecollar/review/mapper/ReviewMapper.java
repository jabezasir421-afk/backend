package com.bluecollar.review.mapper;

import com.bluecollar.customer.mapper.CustomerMapper;
import com.bluecollar.review.dto.ReviewResponse;
import com.bluecollar.review.entity.Review;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewMapper {

    private final CustomerMapper customerMapper;

    public ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBooking().getId(),
                review.getWorker().getId(),
                customerMapper.toSummaryResponse(review.getCustomer()),
                review.getRating(),
                review.getComment(),
                review.getActive(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
