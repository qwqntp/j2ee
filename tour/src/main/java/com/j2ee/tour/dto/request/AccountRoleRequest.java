package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountRoleRequest(
        @NotBlank @Size(max = 50) String roleName
) {
}
