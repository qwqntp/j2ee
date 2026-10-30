package com.j2ee.tour.service;

import com.j2ee.tour.common.PageResponse;
import com.j2ee.tour.dto.request.CustomerRequest;
import com.j2ee.tour.dto.response.CustomerResponse;
import com.j2ee.tour.model.Account;
import com.j2ee.tour.model.Customer;
import com.j2ee.tour.repository.AccountRepository;
import com.j2ee.tour.repository.CustomerRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class CustomerService {
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public CustomerService(CustomerRepository customerRepository, AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Pageable pageable) {
        return PageResponse.from(customerRepository.findAll(pageable), CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByAccount(Long accountId) {
        return CustomerResponse.from(customerRepository.findByAccountAccountId(accountId)
                .orElseThrow(() -> new NoSuchElementException("Customer not found for account")));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long id) {
        customerRepository.delete(find(id));
    }

    private void apply(Customer customer, CustomerRequest request) {
        Account account = request.accountId() == null ? null
                : accountRepository.findById(request.accountId())
                .orElseThrow(() -> new NoSuchElementException("Account not found"));
        customer.setCustomerName(request.name());
        customer.setCustomerEmail(request.email());
        customer.setCustomerPhone(request.phone());
        customer.setCustomerAddress(request.address());
        customer.setCustomerDateOfBirth(request.dateOfBirth());
        customer.setAccount(account);
    }

    private Customer find(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Customer not found"));
    }
}
