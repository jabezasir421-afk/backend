package com.bluecollar.address.mapper;

import com.bluecollar.address.dto.AddressResponse;
import com.bluecollar.address.dto.CreateAddressRequest;
import com.bluecollar.address.dto.UpdateAddressRequest;
import com.bluecollar.address.entity.Address;
import com.bluecollar.customer.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {

    public Address toEntity(CreateAddressRequest request, Customer customer) {
        return Address.builder()
                .customer(customer)
                .label(normalize(request.label()))
                .addressType(request.addressType())
                .line1(normalize(request.line1()))
                .line2(normalize(request.line2()))
                .landmark(normalize(request.landmark()))
                .city(normalize(request.city()))
                .state(normalize(request.state()))
                .pincode(normalize(request.pincode()))
                .latitude(request.latitude())
                .longitude(request.longitude())
                .isDefault(request.isDefault())
                .build();
    }

    public AddressResponse toResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getCustomer().getId(),
                address.getLabel(),
                address.getAddressType(),
                address.getLine1(),
                address.getLine2(),
                address.getLandmark(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLatitude(),
                address.getLongitude(),
                address.getIsDefault(),
                address.getActive(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }

    public void updateEntity(Address address, UpdateAddressRequest request) {
        address.setLabel(normalize(request.label()));
        address.setAddressType(request.addressType());
        address.setLine1(normalize(request.line1()));
        address.setLine2(normalize(request.line2()));
        address.setLandmark(normalize(request.landmark()));
        address.setCity(normalize(request.city()));
        address.setState(normalize(request.state()));
        address.setPincode(normalize(request.pincode()));
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());
        address.setActive(request.active());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
