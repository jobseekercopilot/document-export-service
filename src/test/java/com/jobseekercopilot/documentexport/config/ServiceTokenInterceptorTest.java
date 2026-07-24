package com.jobseekercopilot.documentexport.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

class ServiceTokenInterceptorTest {

    @Test
    void replacesAnyCallerValueWithExactlyOneConfiguredCredential() throws IOException {
        String credential = "test-only-document-store-producer-token-32-bytes";
        HttpHeaders headers = new HttpHeaders();
        headers.add(ServiceTokenInterceptor.SERVICE_TOKEN_HEADER, "untrusted-value");
        HttpRequest request = new TestHttpRequest(headers);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(execution.execute(any(), eq(new byte[0]))).thenReturn(response);

        ClientHttpResponse actual = new ServiceTokenInterceptor(credential)
                .intercept(request, new byte[0], execution);

        assertEquals(response, actual);
        assertEquals(
                credential,
                request.getHeaders().getFirst(ServiceTokenInterceptor.SERVICE_TOKEN_HEADER));
        assertEquals(
                1,
                request.getHeaders().get(ServiceTokenInterceptor.SERVICE_TOKEN_HEADER).size());
        verify(execution).execute(request, new byte[0]);
    }

    private record TestHttpRequest(HttpHeaders getHeaders) implements HttpRequest {
        @Override
        public HttpMethod getMethod() {
            return HttpMethod.GET;
        }

        @Override
        public URI getURI() {
            return URI.create("http://document-store.test/api/v1/documents");
        }
    }
}
