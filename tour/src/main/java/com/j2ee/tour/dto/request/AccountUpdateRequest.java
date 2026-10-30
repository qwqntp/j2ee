package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @Size(min = 8, max = 100) String password,
        @Email @Size(max = 255) String email,
        @NotNull @Min(0) @Max(1) Integer emailVerified,
        @NotNull @Min(0) @Max(1) Integer status,
        @NotNull Long roleId
) {
}
