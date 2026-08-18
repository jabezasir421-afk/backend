package com.bluecollar.address.service;

import com.bluecollar.address.dto.AddressResponse;
import com.bluecollar.address.dto.CreateAddressRequest;
import com.bluecollar.address.dto.UpdateAddressRequest;

import java.util.List;
import java.util.UUID;

public interface AddressService {

    AddressResponse createAddress(CreateAddressRequest request);

    List<AddressResponse> getMyAddresses();

    AddressResponse getMyAddressById(UUID id);

    AddressResponse updateAddress(UUID id, UpdateAddressRequest request);

    void deleteAddress(UUID id);

    AddressResponse setDefaultAddress(UUID id);
}
