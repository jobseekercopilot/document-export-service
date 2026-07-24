package com.jobseekercopilot.documentexport.config;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

final class ServiceTokenInterceptor implements ClientHttpRequestInterceptor {

    static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final String serviceToken;

    ServiceTokenInterceptor(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        HttpHeaders headers = request.getHeaders();
        headers.set(SERVICE_TOKEN_HEADER, serviceToken);
        return execution.execute(request, body);
    }
}
