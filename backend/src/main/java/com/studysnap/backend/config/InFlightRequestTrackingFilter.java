package com.studysnap.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Tracks only the path and lifetime of requests currently inside the application filter chain.
 *
 * <p>Ordered ahead of the Spring Security chain (mirroring {@link RequestIdFilter}) so that a
 * request blocked inside a security filter's own DB access — e.g. {@code JwtAuthenticationFilter}'s
 * per-request user lookup — is still visible to {@link PoolSaturationDetector}'s snapshot. Without
 * this, the default filter order (LOWEST_PRECEDENCE) placed this filter after security, so any
 * connection held while authenticating never appeared in the in-flight list.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class InFlightRequestTrackingFilter extends OncePerRequestFilter {
    private final InFlightRequestRegistry registry;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        registry.registerCurrentThread(request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            registry.removeCurrentThread();
        }
    }
}
