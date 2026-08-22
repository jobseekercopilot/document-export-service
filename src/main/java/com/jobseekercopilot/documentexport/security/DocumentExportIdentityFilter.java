package com.jobseekercopilot.documentexport.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.documentexport.dto.ServiceIdentityError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class DocumentExportIdentityFilter extends OncePerRequestFilter {

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";
    public static final String OWNER_HEADER = "X-Document-Owner";
    public static final String OWNER_ATTRIBUTE = "documentExportOwner";

    private static final String PROTECTED_PATH = "/api/v1/document-exports";

    private final DocumentExportCredentials credentials;
    private final ObjectMapper objectMapper;

    public DocumentExportIdentityFilter(
            DocumentExportCredentials credentials,
            ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PROTECTED_PATH) || path.startsWith(PROTECTED_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> serviceTokens = headers(request, SERVICE_TOKEN_HEADER);
        if (serviceTokens.size() != 1
                || !StringUtils.hasText(serviceTokens.get(0))
                || !matches(serviceTokens.get(0), credentials.gatewayToken())) {
            reject(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED",
                    "Valid service authentication is required.");
            return;
        }

        List<String> owners = headers(request, OWNER_HEADER);
        if (owners.size() != 1 || !StringUtils.hasText(owners.get(0))) {
            reject(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "OWNER_CONTEXT_REQUIRED",
                    "Exactly one document owner context is required.");
            return;
        }

        request.setAttribute(OWNER_ATTRIBUTE, owners.get(0).trim());
        filterChain.doFilter(request, response);
    }

    private List<String> headers(HttpServletRequest request, String name) {
        return Collections.list(request.getHeaders(name));
    }

    private boolean matches(String supplied, String expected) {
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(
            HttpServletResponse response,
            int status,
            String code,
            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                new ServiceIdentityError(code, message));
    }
}
