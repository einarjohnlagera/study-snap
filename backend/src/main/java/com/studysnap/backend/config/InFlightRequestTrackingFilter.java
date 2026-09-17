package com.studysnap.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Tracks only the path and lifetime of requests currently inside the application filter chain. */
@Component
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
