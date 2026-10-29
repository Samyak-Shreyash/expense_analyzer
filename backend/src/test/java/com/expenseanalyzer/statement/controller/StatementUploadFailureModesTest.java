package com.expenseanalyzer.statement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.messaging.repository.OutboxEventRepository;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Failure modes that need their own setup: a tiny validator size limit (413 from the validator, which
 * sits below the multipart limit, so MockMvc can exercise it) and a failing StatementStorage (503).
 */
@SpringBootTest(properties = "app.statement.upload.max-size-bytes=64")
class StatementUploadFailureModesTest {

    private static final String EMAIL = "alice@example.com";
    private static final byte[] SMALL_CSV = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private StatementRepository statementRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @MockitoBean private StatementStorage storage;
    @MockitoBean private UserRepository userRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(EMAIL);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
    }

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        statementRepository.deleteAll();
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
    }

    private MockMultipartHttpServletRequestBuilder csv(byte[] bytes) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/statements")
                .file(new MockMultipartFile("file", "s.csv", "text/csv", bytes))
                .header("Authorization", bearer());
    }

    @Test
    void oversizeFileIs413FileTooLargeButSmallFileIsAccepted() throws Exception {
        mockMvc.perform(csv(SMALL_CSV)).andExpect(status().isCreated());

        mockMvc.perform(csv(("x,y\n" + "1,2\n".repeat(100)).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("FILE_TOO_LARGE"));
    }

    @Test
    void storageFailureIs503AndRollsBackTheRecord() throws Exception {
        // Control: with storage working, an upload is accepted and persisted.
        mockMvc.perform(csv(SMALL_CSV)).andExpect(status().isCreated());
        assertThat(statementRepository.count()).isEqualTo(1);

        doThrow(SdkClientException.create("down")).when(storage).store(anyString(), any(), anyLong(), anyString());
        mockMvc.perform(csv("c,d\n3,4\n".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Storage unavailable"));
        assertThat(statementRepository.count()).isEqualTo(1);
    }

    @Test
    void storageOutageOnDeleteIs503AndTheRowSurvives() throws Exception {
        String body = mockMvc.perform(csv(SMALL_CSV)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        doThrow(SdkClientException.create("down")).when(storage).delete(anyString());

        mockMvc.perform(delete("/api/statements/" + id).header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Storage unavailable"));
        assertThat(statementRepository.findById(id)).isPresent();
    }
}
