package com.expenseanalyzer.statement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Runs on a real Tomcat because MockMvc does not enforce servlet multipart limits.
 * The limits are lowered so the test does not need a 20 MB body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=2KB",
        "server.tomcat.max-swallow-size=-1"
})
class StatementUploadLimitsTest {

    private static final String EMAIL = "alice@example.com";
    private static final String BOUNDARY = "testboundary";

    @LocalServerPort private int port;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private UserRepository userRepository;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        User alice = new User();
        alice.setId(UUID.randomUUID());
        alice.setEmail(EMAIL);
        alice.setFullName("Alice");
        alice.setRole(UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(alice));
    }

    @Test
    void overLimitMultipartReturns413ProblemDetail() throws Exception {
        HttpResponse<String> res = post(part("file", "big.csv", "a".repeat(4096)));

        assertThat(res.statusCode()).isEqualTo(413);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                ct -> assertThat(ct).startsWith("application/problem+json"));
        assertThat(res.body()).contains("\"reason\":\"FILE_TOO_LARGE\"").contains("\"title\":\"Invalid file\"");
    }

    @Test
    void missingFilePartReturns400ProblemDetail() throws Exception {
        HttpResponse<String> res = post(part("other", "x.csv", "a,b\n"));

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.body()).contains("\"code\":\"STATEMENT_FILE_MISSING\"");
    }

    private static String part(String name, String filename, String content) {
        return "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: text/csv\r\n\r\n"
                + content + "\r\n"
                + "--" + BOUNDARY + "--\r\n";
    }

    private HttpResponse<String> post(String body) throws Exception {
        String token = jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/statements"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
