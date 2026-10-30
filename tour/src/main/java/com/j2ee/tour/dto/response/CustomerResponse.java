package com.j2ee.tour.dto.response;

import com.j2ee.tour.model.Customer;

import java.time.LocalDate;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        String phone,
        String address,
        LocalDate dateOfBirth,
        Long accountId
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getCustomerName(),
                customer.getCustomerEmail(),
                customer.getCustomerPhone(),
                customer.getCustomerAddress(),
                customer.getCustomerDateOfBirth(),
                customer.getAccount() == null ? null : customer.getAccount().getAccountId()
        );
    }
}
