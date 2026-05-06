package com.studysnap.backend.service;

public interface GoogleIdentityTokenVerifier {
    GoogleIdentity verify(String credential);

    record GoogleIdentity(
            String subject,
            String email,
            boolean emailVerified,
            String name,
            String givenName
    ) {
    }
}
