package com.expenseanalyzer.statement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.common.exception.StatementObjectNotFoundException;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Statement API through the full security chain, with real users/statements on H2 and the
 * in-memory storage. Oversize (413) is covered by StatementUploadLimitsTest (servlet limit) and
 * StatementUploadFailureModesTest (validator limit); storage failure (503) by the latter too.
 */
@SpringBootTest
class StatementControllerSecurityTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final byte[] CSV = "date,amount\n2026-01-01,1\n".getBytes(StandardCharsets.UTF_8);

    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;
    @Autowired private StatementRepository statementRepository;
    @Autowired private StatementStorage storage;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        statementRepository.deleteAll();
        userRepository.deleteAll();
        createUser(ALICE);
        createUser(BOB);
    }

    private void createUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("x");
        u.setFullName(email);
        u.setRole(UserRole.USER);
        userRepository.save(u);
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private MvcResult upload(String email, String filename, String contentType, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/statements")
                .file(new MockMultipartFile("file", filename, contentType, bytes))
                .header("Authorization", bearer(email))).andReturn();
    }

    private UUID uploadCsv(String email, byte[] bytes) throws Exception {
        MvcResult res = upload(email, "s.csv", "text/csv", bytes);
        assertThat(res.getResponse().getStatus()).isEqualTo(202);

        return UUID.fromString(JsonPath.read(res.getResponse().getContentAsString(), "$.id"));
    }

    private String keyOf(UUID id) {
        return statementRepository.findById(id).orElseThrow().getObjectKey();
    }

    @Test
    void uploadWithoutTokenIs401ButWithTokenIs202() throws Exception {
        mockMvc.perform(multipart("/api/statements").file(new MockMultipartFile("file", "s.csv", "text/csv", CSV)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/statements").file(new MockMultipartFile("file", "s.csv", "text/csv", CSV))
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isAccepted());
    }

    @Test
    void validCsvIsQueuedAndStored() throws Exception {
        mockMvc.perform(multipart("/api/statements").file(new MockMultipartFile("file", "s.csv", "text/csv", CSV))
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.id").exists());
        Statement saved = statementRepository.findAll().get(0);
        assertThat(storage.get(saved.getObjectKey())).isEqualTo(CSV);
    }

    @Test
    void badExtensionIs400UnsupportedExtension() throws Exception {
        // Control: same bytes with a .csv name are accepted.
        assertThat(upload(ALICE, "s.csv", "text/csv", CSV).getResponse().getStatus()).isEqualTo(202);

        mockMvc.perform(multipart("/api/statements")
                        .file(new MockMultipartFile("file", "malware.exe", "application/octet-stream", CSV))
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("UNSUPPORTED_EXTENSION"));
    }

    @Test
    void signatureMismatchIs400() throws Exception {
        mockMvc.perform(multipart("/api/statements")
                        .file(new MockMultipartFile("file", "s.pdf", "application/pdf", CSV))
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("SIGNATURE_MISMATCH"));
        // Control: real PDF magic passes with the same extension and MIME type.
        byte[] pdf = "%PDF-1.7\nbody".getBytes(StandardCharsets.UTF_8);
        assertThat(upload(ALICE, "s.pdf", "application/pdf", pdf).getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void sameUserSameBytesTwiceIs409WithOnlyId() throws Exception {
        UUID first = uploadCsv(ALICE, CSV);

        mockMvc.perform(multipart("/api/statements").file(new MockMultipartFile("file", "again.csv", "text/csv", CSV))
                        .header("Authorization", bearer(ALICE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATEMENT_DUPLICATE"))
                .andExpect(jsonPath("$.id").value(first.toString()))
                .andExpect(jsonPath("$.fileName").doesNotExist())
                .andExpect(jsonPath("$.status").value(409)); // ProblemDetail status, not a statement status
        assertThat(statementRepository.count()).isEqualTo(1);
    }

    @Test
    void differentUserSameBytesIs202() throws Exception {
        UUID alices = uploadCsv(ALICE, CSV);
        UUID bobs = uploadCsv(BOB, CSV);
        assertThat(bobs).isNotEqualTo(alices);
    }

    @Test
    void otherUsersStatementIs404ForGetStatusAndDeleteButOwnerSucceeds() throws Exception {
        UUID id = uploadCsv(ALICE, CSV);
        String key = keyOf(id);

        // Controls: the owner can read both views.
        mockMvc.perform(get("/api/statements/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/statements/" + id + "/status").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/statements/" + id).header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STATEMENT_NOT_FOUND"));
        mockMvc.perform(get("/api/statements/" + id + "/status").header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STATEMENT_NOT_FOUND"));
        mockMvc.perform(delete("/api/statements/" + id).header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STATEMENT_NOT_FOUND"));

        assertThat(statementRepository.findById(id)).isPresent();
        assertThat(storage.get(key)).isEqualTo(CSV);
    }

    @Test
    void listReturnsOnlyCallersStatements() throws Exception {
        UUID alices = uploadCsv(ALICE, CSV);
        UUID bobs = uploadCsv(BOB, "other,data\n1,2\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/statements").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(alices.toString()));
        mockMvc.perform(get("/api/statements").header("Authorization", bearer(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bobs.toString()));
        mockMvc.perform(get("/api/statements")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerDeleteReturns204AndRemovesObject() throws Exception {
        UUID id = uploadCsv(ALICE, CSV);
        String key = keyOf(id);
        assertThat(storage.get(key)).isEqualTo(CSV);

        mockMvc.perform(delete("/api/statements/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNoContent());

        assertThat(statementRepository.findById(id)).isEmpty();
        assertThatThrownBy(() -> storage.get(key)).isInstanceOf(StatementObjectNotFoundException.class);
        mockMvc.perform(get("/api/statements/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNotFound());
    }
}
