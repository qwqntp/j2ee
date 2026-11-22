package com.j2ee.tour.service;

/**
 * Verifies a Google Identity Services ID token. Kept behind an interface so tests
 * never call Google's JWKS endpoint.
 */
public interface GoogleIdTokenVerifierService {

    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String subject, String email, boolean emailVerified) {
    }
}
