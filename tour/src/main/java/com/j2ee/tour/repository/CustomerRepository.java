package com.j2ee.tour.repository;

import com.j2ee.tour.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByAccountAccountId(Long accountId);
}
