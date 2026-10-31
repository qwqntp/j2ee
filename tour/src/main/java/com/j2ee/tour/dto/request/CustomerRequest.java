package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 20) String phone,
        @Size(max = 255) String address,
        LocalDate dateOfBirth,
        Long accountId
) {
}
