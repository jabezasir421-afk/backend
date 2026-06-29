package com.bluecollar.customer.service;

import com.bluecollar.auth.entity.UserAccount;
import com.bluecollar.auth.repository.UserAccountRepository;
import com.bluecollar.common.security.AuthenticatedUser;
import com.bluecollar.common.security.SecurityUtils;
import com.bluecollar.customer.dto.CustomerResponse;
import com.bluecollar.customer.dto.UpdateCustomerRequest;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.exception.CustomerNotFoundException;
import com.bluecollar.customer.mapper.CustomerMapper;
import com.bluecollar.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final UserAccountRepository userAccountRepository;
    private final CustomerMapper customerMapper;

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getMyProfile() {
        return customerMapper.toResponse(findCustomerByCurrentUser());
    }

    @Override
    public CustomerResponse updateMyProfile(UpdateCustomerRequest request) {
        Customer customer = findCustomerByCurrentUser();
        customerMapper.updateEntity(customer, request);
        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID id) {
        return customerMapper.toResponse(findCustomer(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable).map(customerMapper::toResponse);
    }

    @Override
    public void deactivateCustomer(UUID id) {
        Customer customer = findCustomer(id);
        customer.setActive(false);
        customerRepository.save(customer);

        UserAccount userAccount = customer.getUserAccount();
        userAccount.setActive(false);
        userAccountRepository.save(userAccount);
    }

    public Customer findCustomerByCurrentUser() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return customerRepository.findByUserAccountId(currentUser.userAccountId())
                .orElseThrow(() -> new CustomerNotFoundException("Customer profile not found for current user"));
    }

    public Customer findCustomer(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }
}
