package com.bluecollar.address.repository;

import com.bluecollar.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(UUID customerId);

    long countByCustomerIdAndActiveTrue(UUID customerId);

    Optional<Address> findByIdAndCustomerId(UUID id, UUID customerId);

    List<Address> findByCustomerIdAndIsDefaultTrueAndActiveTrue(UUID customerId);
}
