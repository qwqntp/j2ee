package com.j2ee.tour.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @NotBlank
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 255)
    private String accountEmail;

    @NotNull
    @Min(0)
    @Max(1)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer emailVerified;

    @NotNull
    @Min(0)
    @Max(1)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(nullable = false)
    private Integer status;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime accountCreatedAt;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime accountUpdatedAt;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private AccountRole role;
}
