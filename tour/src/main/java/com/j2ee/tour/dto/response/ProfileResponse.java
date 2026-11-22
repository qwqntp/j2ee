package com.j2ee.tour.dto.response;

import java.time.LocalDate;

/**
 * The authenticated user's profile. {@code customerId}, {@code name}, {@code phone},
 * {@code address} and {@code dateOfBirth} are null when the account has no customer row yet.
 * {@code email} always comes from {@code accounts.account_email}, the authoritative field.
 */
public record ProfileResponse(
        Long customerId,
        String username,
        String email,
        Integer emailVerified,
        String name,
        String phone,
        String address,
        LocalDate dateOfBirth,
        boolean passwordSet,
        boolean googleLinked
) {
}
