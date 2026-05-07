package com.studysnap.backend.service;

public interface GoogleIdentityTokenVerifier {
    GoogleIdentity verify(String code);

    record GoogleIdentity(
            String subject,
            String email,
            boolean emailVerified,
            String name,
            String givenName
    ) {
    }
}
