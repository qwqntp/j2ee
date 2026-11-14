package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Self-service profile update. Email is optional: an omitted or blank value keeps the current
 * account email. Unknown JSON fields (for example {@code accountId}) are ignored.
 */
public record ProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        String name,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone,

        @Size(max = 255, message = "Address must be at most 255 characters")
        String address,

        @PastOrPresent(message = "Date of birth must not be in the future")
        LocalDate dateOfBirth
) {
}
