package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    private String username;
    private String password;
    private String accountEmail;   // chua co du lieu trong seed; signup/OAuth can
    private Integer emailVerified; // seed nap 1/0 => SMALLINT
    private Integer status;
    private LocalDateTime accountCreatedAt;
    private LocalDateTime accountUpdatedAt;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private AccountRole role;
}
