package com.j2ee.tour.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "account_roles")
public class AccountRole {
    @Id
    private String accountRoleId;

    private String roleName;
}
