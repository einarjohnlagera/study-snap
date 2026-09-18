package com.studysnap.backend.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InFlightRequestTrackingFilterTest {
    private InFlightRequestRegistry registry;
    private InFlightRequestTrackingFilter filter;

    @BeforeEach
    void setUp() {
        registry = new InFlightRequestRegistry();
        filter = new InFlightRequestTrackingFilter(registry);
    }

    @Test
    void tracksThePathOnlyWhileTheRequestIsInsideTheFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes/public/note-1");

        filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
            assertThat(registry.snapshot()).containsKey(Thread.currentThread());
            assertThat(registry.snapshot().get(Thread.currentThread()).path())
                    .isEqualTo("/api/notes/public/note-1");
        });

        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void removesTheEntryWhenTheDownstreamChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes/public/note-2");

        assertThatThrownBy(() -> filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                    assertThat(registry.snapshot()).containsKey(Thread.currentThread());
                    throw new ServletException("downstream failure");
                }
        )).isInstanceOf(ServletException.class)
                .hasMessage("downstream failure");

        assertThat(registry.snapshot()).isEmpty();
    }
}
