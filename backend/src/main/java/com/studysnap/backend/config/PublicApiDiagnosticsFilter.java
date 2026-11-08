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

/** Observes only the public library API; no diagnostic failure may affect its HTTP contract. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class PublicApiDiagnosticsFilter extends OncePerRequestFilter {
    static final String NOTES_PUBLIC = "/notes/public";
    static final String PUBLIC_ROOT = "/public";
    static final String TAGS = "/tags";
    static final String SUBJECTS = "/subjects";
    static final String COURSE_PROGRAMS = "/course-programs";
    static final String COLLECTIONS_PUBLIC = "/collections/public";
    private final PublicApiDiagnosticsAggregator diagnostics;
    private final StudySnapProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean enabled;
        try {
            enabled = properties.getPublicApiDiagnostics().isEnabled();
        } catch (Throwable failure) {
            noteFailure();
            chain.doFilter(request, response);
            return;
        }
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        boolean observe = false;
        boolean options = false;
        try {
            String path = request.getServletPath();
            if (isPublicPath(path)) {
                String method = request.getMethod();
                observe = "GET".equals(method) || "HEAD".equals(method);
                options = "OPTIONS".equals(method);
            }
        } catch (Throwable failure) {
            noteFailure();
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (observe || options) {
                try {
                    diagnostics.record(request, response.getStatus(), options);
                } catch (Throwable failure) {
                    noteFailure();
                }
            }
        }
    }

    private void noteFailure() {
        try {
            diagnostics.recordErrorSafely();
        } catch (Throwable ignored) {
            // The counter is diagnostic too, and must not affect the HTTP response.
        }
    }

    static boolean isPublicPath(String path) {
        return path != null && (path.equals(NOTES_PUBLIC) || path.startsWith(NOTES_PUBLIC + "/")
                || path.equals(PUBLIC_ROOT) || path.startsWith(PUBLIC_ROOT + "/")
                || path.equals(TAGS) || path.equals(SUBJECTS) || path.equals(COURSE_PROGRAMS)
                || path.equals(COLLECTIONS_PUBLIC) || path.startsWith(COLLECTIONS_PUBLIC + "/"));
    }
}
