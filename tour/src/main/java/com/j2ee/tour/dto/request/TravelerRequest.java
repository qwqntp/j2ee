package com.j2ee.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TravelerRequest(
        @NotNull Long bookingId,
        @NotBlank @Size(max = 255) String name,
        LocalDate dateOfBirth,
        @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
        @Size(max = 64) String idDocument,
        @Size(max = 20) String phone,
        @Email @Size(max = 255) String email
) {
    public TravelerRequest(Long bookingId, String name, LocalDate dateOfBirth,
                           String gender, String idDocument) {
        this(bookingId, name, dateOfBirth, gender, idDocument, null, null);
    }
}
