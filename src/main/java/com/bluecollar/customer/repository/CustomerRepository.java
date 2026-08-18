package com.bluecollar.customer.repository;

import com.bluecollar.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByUserAccountId(UUID userAccountId);

    long countByActiveTrue();
}
