package com.bluecollar.customer.mapper;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.customer.dto.CustomerResponse;
import com.bluecollar.customer.dto.CustomerSummaryResponse;
import com.bluecollar.customer.dto.UpdateCustomerRequest;
import com.bluecollar.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(UserAccount userAccount, String firstName, String lastName) {
        return Customer.builder()
                .userAccount(userAccount)
                .firstName(normalize(firstName))
                .lastName(normalize(lastName))
                .build();
    }

    public CustomerResponse toResponse(Customer customer) {
        UserAccount userAccount = customer.getUserAccount();
        return new CustomerResponse(
                customer.getId(),
                userAccount.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                userAccount.getEmail(),
                userAccount.getPhoneNumber(),
                customer.getProfilePhotoFileId(),
                customer.getActive(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    public CustomerSummaryResponse toSummaryResponse(Customer customer) {
        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName()
        );
    }

    public void updateEntity(Customer customer, UpdateCustomerRequest request) {
        customer.setFirstName(normalize(request.firstName()));
        customer.setLastName(normalize(request.lastName()));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
