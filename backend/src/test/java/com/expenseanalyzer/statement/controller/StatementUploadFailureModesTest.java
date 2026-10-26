package com.expenseanalyzer.statement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.common.exception.StatementStorageException;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

/**
 * Failure modes that need a different setup from StatementControllerSecurityTest: a mocked
 * StatementStorage (503) and a tiny validator size limit (413 via the validator, which sits below
 * the multipart limit, so MockMvc can exercise it).
 */
@SpringBootTest(properties = "app.statement.upload.max-size-bytes=64")
class StatementUploadFailureModesTest {

    private static final String EMAIL = "alice@example.com";
    private static final byte[] SMALL_CSV = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private StatementRepository statementRepository;
    @MockitoBean private StatementStorage storage;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        statementRepository.deleteAll();
        userRepository.deleteAll();
        User u = new User();
        u.setEmail(EMAIL);
        u.setPasswordHash("x");
        u.setFullName("Alice");
        u.setRole(UserRole.USER);
        userRepository.save(u);
    }

    private MockMultipartHttpServletRequestBuilder csv(byte[] bytes) {
        String token = jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/statements")
                .file(new MockMultipartFile("file", "s.csv", "text/csv", bytes))
                .header("Authorization", "Bearer " + token);
    }

    @Test
    void oversizeFileIs413FileTooLargeButSmallFileIs202() throws Exception {
        mockMvc.perform(csv(SMALL_CSV)).andExpect(status().isAccepted());

        mockMvc.perform(csv(("x,y\n" + "1,2\n".repeat(100)).getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.reason").value("FILE_TOO_LARGE"));
    }

    @Test
    void storageFailureIs503AndRollsBackTheRecord() throws Exception {
        // Control: with the mock storage not failing, an upload is accepted and persisted.
        mockMvc.perform(csv(SMALL_CSV)).andExpect(status().isAccepted());
        assertThat(statementRepository.count()).isEqualTo(1);

        doThrow(new StatementStorageException("down", null)).when(storage).put(anyString(), any(), any());
        mockMvc.perform(csv("c,d\n3,4\n".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STATEMENT_STORAGE_UNAVAILABLE"));
        assertThat(statementRepository.count()).isEqualTo(1);
    }
}
