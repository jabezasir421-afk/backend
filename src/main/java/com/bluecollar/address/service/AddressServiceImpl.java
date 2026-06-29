package com.bluecollar.address.service;

import com.bluecollar.address.dto.AddressResponse;
import com.bluecollar.address.dto.CreateAddressRequest;
import com.bluecollar.address.dto.UpdateAddressRequest;
import com.bluecollar.address.entity.Address;
import com.bluecollar.address.exception.AddressNotFoundException;
import com.bluecollar.address.exception.MaxAddressesExceededException;
import com.bluecollar.address.mapper.AddressMapper;
import com.bluecollar.address.repository.AddressRepository;
import com.bluecollar.customer.entity.Customer;
import com.bluecollar.customer.service.CustomerServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AddressServiceImpl implements AddressService {

    private static final int MAX_ACTIVE_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final CustomerServiceImpl customerService;
    private final AddressMapper addressMapper;

    @Override
    public AddressResponse createAddress(CreateAddressRequest request) {
        Customer customer = customerService.findCustomerByCurrentUser();
        validateAddressLimit(customer.getId());

        Address address = addressMapper.toEntity(request, customer);
        if (shouldBeDefault(customer.getId(), request.isDefault())) {
            clearDefaultAddresses(customer.getId());
            address.setIsDefault(true);
        }

        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {
        Customer customer = customerService.findCustomerByCurrentUser();
        return addressRepository.findByCustomerIdAndActiveTrueOrderByIsDefaultDescCreatedAtDesc(customer.getId())
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getMyAddressById(UUID id) {
        return addressMapper.toResponse(findOwnedAddress(id));
    }

    @Override
    public AddressResponse updateAddress(UUID id, UpdateAddressRequest request) {
        Address address = findOwnedAddress(id);
        addressMapper.updateEntity(address, request);
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    public void deleteAddress(UUID id) {
        Address address = findOwnedAddress(id);
        address.setActive(false);
        address.setIsDefault(false);
        addressRepository.save(address);
    }

    @Override
    public AddressResponse setDefaultAddress(UUID id) {
        Address address = findOwnedAddress(id);
        clearDefaultAddresses(address.getCustomer().getId());
        address.setIsDefault(true);
        address.setActive(true);
        return addressMapper.toResponse(addressRepository.save(address));
    }

    public Address findActiveAddressForCustomer(UUID addressId, UUID customerId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .filter(Address::getActive)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private Address findOwnedAddress(UUID id) {
        Customer customer = customerService.findCustomerByCurrentUser();
        return addressRepository.findByIdAndCustomerId(id, customer.getId())
                .orElseThrow(() -> new AddressNotFoundException(id));
    }

    private void validateAddressLimit(UUID customerId) {
        if (addressRepository.countByCustomerIdAndActiveTrue(customerId) >= MAX_ACTIVE_ADDRESSES) {
            throw new MaxAddressesExceededException(MAX_ACTIVE_ADDRESSES);
        }
    }

    private boolean shouldBeDefault(UUID customerId, Boolean requestedDefault) {
        if (Boolean.TRUE.equals(requestedDefault)) {
            return true;
        }
        return addressRepository.countByCustomerIdAndActiveTrue(customerId) == 0;
    }

    private void clearDefaultAddresses(UUID customerId) {
        addressRepository.findByCustomerIdAndIsDefaultTrueAndActiveTrue(customerId)
                .forEach(address -> address.setIsDefault(false));
    }
}
