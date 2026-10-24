package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String customerName;

    @Column(length = 255)
    private String customerEmail;

    @Column(length = 20)
    private String customerPhone;

    @Column(length = 255)
    private String customerAddress;

    private LocalDate customerDateOfBirth;

    @OneToOne
    @JoinColumn(name = "account_id", unique = true)
    private Account account;
}
