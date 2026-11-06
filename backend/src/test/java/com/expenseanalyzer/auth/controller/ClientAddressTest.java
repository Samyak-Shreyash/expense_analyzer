package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.config.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** Runs a real server: the forwarded address is resolved by Tomcat, which MockMvc skips. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=native")
class ClientAddressTest {

    private static final String CLIENT = "203.0.113.9";
    private static final String SPOOFED = "198.51.100.7";

    @LocalServerPort
    private int port;
    @MockitoBean
    private RateLimiter rateLimiter;

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", SPOOFED + ", " + CLIENT + ", 172.18.0.5")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void loginIsLimitedOnTheAddressNextToOurProxyNotOneTheClientCanSpoof() throws Exception {
        post("/api/auth/login", "{\"email\":\"nobody@example.com\",\"password\":\"long-enough-1\"}");

        verify(rateLimiter).checkLogin("nobody@example.com", CLIENT);
    }

    @Test
    void refreshIsLimitedOnTheClientAddress() throws Exception {
        post("/api/auth/refresh", "");

        verify(rateLimiter).checkRefresh(CLIENT);
    }

    @Test
    void refreshAboveTheLimitAnswers429() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)).when(rateLimiter).checkRefresh(anyString());

        assertThat(post("/api/auth/refresh", "").statusCode()).isEqualTo(429);
    }

    @Test
    void productionConfigTrustsForwardedHeaders() {
        // src/test/resources/application.yml shadows the main file on the classpath, so read the main one directly.
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yml"));

        assertThat(yaml.getObject().getProperty("server.forward-headers-strategy")).isEqualTo("native");
    }

    @Test
    void registerIsLimitedOnTheClientAddress() throws Exception {
        post("/api/auth/register", "{\"email\":\"new-client-address@example.com\",\"password\":\"long-enough-1\"}");

        verify(rateLimiter).checkRegister(CLIENT);
    }
}
