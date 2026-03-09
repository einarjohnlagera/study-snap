package com.studysnap.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.config.RequestIdFilter;
import com.studysnap.backend.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        String requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR) == null
                ? "unknown"
                : request.getAttribute(RequestIdFilter.REQUEST_ID_ATTR).toString();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                requestId,
                new ApiErrorResponse.ApiError("FORBIDDEN", "You do not have permission to access this endpoint.", null)
        ));
    }
}
