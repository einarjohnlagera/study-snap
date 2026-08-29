package com.studysnap.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every invitation-link endpoint, asserted anonymous-rejected.
 *
 * <p>⚠️ This class used to cover ONE of the five. {@code .anyRequest().authenticated()} made the
 * rest safe, but a single canary is the whole guard: the day someone adds a {@code permitAll}
 * matcher, or moves an endpoint under a path that already has one, nothing here would notice. The
 * link path is the one that can form a cross-user permission relationship from a token alone, which
 * is why it gets full coverage rather than a sample.
 *
 * <p>⚠️ Anonymous access must be rejected as 401 and must NOT disclose whether a token or link
 * exists — the single not-found contract for unknown, revoked, expired and redeemed tokens is a
 * v0.90.0 property closing the account-existence oracle, and an auth failure must not become a
 * cheaper oracle than the one it protects.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LinkedLearnerInvitationLinkSecurityIntegrationTest {
    private static final String TOKEN = "AbCdEf0123456789GhIjKl";
    private static final String LINKS = "/linked-learners/invitation-links";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resolvingAConnectionInvitationTokenRequiresAuthentication() throws Exception {
        mockMvc.perform(get(LINKS + "/" + TOKEN + "/resolve"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void creatingAnInvitationLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post(LINKS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingInvitationLinksRequiresAuthentication() throws Exception {
        mockMvc.perform(get(LINKS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokingAnInvitationLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post(LINKS + "/" + UUID.randomUUID() + "/revoke"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * ⚠️ The sharpest of the five: redemption is what creates the relationship, so an unauthenticated
     * caller reaching it would form a cross-user link with no account behind it.
     */
    @Test
    void redeemingAnInvitationLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post(LINKS + "/" + TOKEN + "/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"learnerBirthYear\":2010}"))
                .andExpect(status().isUnauthorized());
    }
}
