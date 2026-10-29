package com.expenseanalyzer.statement.controller;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.messaging.model.OutboxEvent;
import com.expenseanalyzer.messaging.repository.OutboxEventRepository;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import com.expenseanalyzer.statement.model.Statement;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class StatementControllerTest {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";
    private static final byte[] CSV = "Date,Description,Amount\n2026-01-02,Coffee,-4.50\n".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private StatementStorage storage;

    private MockMvc mockMvc;
    private User alice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(CorrelationIdFilter.class)) // runs before security, as in the app
                .apply(springSecurity()).build();
        alice = user(ALICE);
        user(BOB);
    }

    @AfterEach
    void cleanUp() {
        statementRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    private User user(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setFullName(email);
        u.setRole(UserRole.USER);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    private String bearer(String email) {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(email, null));
    }

    private static MockMultipartFile csv(byte[] body) {
        return new MockMultipartFile("file", "statement.csv", "text/csv", body);
    }

    private String upload(String email, byte[] body) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/statements").file(csv(body)).header("Authorization", bearer(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void uploadStoresFileAndPersistsMetadata() throws Exception {
        mockMvc.perform(multipart("/api/statements").file(csv(CSV)).header("Authorization", bearer(ALICE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fileName").value("statement.csv"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        Statement saved = statementRepository.findAll().getFirst();
        assertThat(saved.getUserId()).isEqualTo(alice.getId());
        assertThat(saved.getFileHash()).hasSize(64);
        assertThat(saved.getStorageKey()).isEqualTo(StatementStorage.keyFor(alice.getId(), saved.getId()));
        verify(storage).store(eq(saved.getStorageKey()), any(), eq((long) CSV.length), eq("text/csv"));
    }

    @Test
    void uploadEnqueuesStatementUploadedEventWithCorrelationId() throws Exception {
        mockMvc.perform(multipart("/api/statements").file(csv(CSV))
                        .header("Authorization", bearer(ALICE))
                        .header("X-Correlation-ID", "corr-upload-1"))
                .andExpect(status().isCreated());

        Statement saved = statementRepository.findAll().getFirst();
        OutboxEvent event = outboxRepository.findAll().getFirst();
        assertThat(event.getEventType()).isEqualTo("StatementUploaded");
        assertThat(event.getAggregateId()).isEqualTo(saved.getId());
        String json = event.getPayload();
        assertThat((String) JsonPath.read(json, "$.correlationId")).isEqualTo("corr-upload-1");
        assertThat((String) JsonPath.read(json, "$.payload.storageKey")).isEqualTo(saved.getStorageKey());
        assertThat((String) JsonPath.read(json, "$.payload.format")).isEqualTo("CSV");
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/statements").file(csv(CSV)))
                .andExpect(status().isUnauthorized());
        verify(storage, never()).store(anyString(), any(), anyLong(), anyString());
    }

    @Test
    void duplicateUploadReturnsConflictWithExistingId() throws Exception {
        String first = upload(ALICE, CSV);

        mockMvc.perform(multipart("/api/statements").file(csv(CSV)).header("Authorization", bearer(ALICE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.statementId").value(first));
        assertThat(statementRepository.count()).isEqualTo(1);
    }

    @Test
    void sameFileFromAnotherUserIsNotADuplicate() throws Exception {
        upload(ALICE, CSV);
        upload(BOB, CSV);
        assertThat(statementRepository.count()).isEqualTo(2);
    }

    @Test
    void invalidFileIsRejected() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "statement.exe", "application/octet-stream", CSV);
        mockMvc.perform(multipart("/api/statements").file(exe).header("Authorization", bearer(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("UNSUPPORTED_EXTENSION"));
        assertThat(statementRepository.count()).isZero();
    }

    @Test
    void pdfIsRejectedUntilAParserExists() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "s.pdf", "application/pdf",
                "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(multipart("/api/statements").file(pdf).header("Authorization", bearer(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("UNSUPPORTED_EXTENSION"));
        assertThat(statementRepository.count()).isZero();
    }

    @Test
    void storageFailureReturns503AndPersistsNothing() throws Exception {
        doThrow(SdkClientException.create("down")).when(storage).store(anyString(), any(), anyLong(), anyString());

        mockMvc.perform(multipart("/api/statements").file(csv(CSV)).header("Authorization", bearer(ALICE)))
                .andExpect(status().isServiceUnavailable());
        assertThat(statementRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    void listReturnsOnlyOwnStatements() throws Exception {
        String mine = upload(ALICE, CSV);
        upload(BOB, "Date,Description,Amount\n2026-01-03,Tea,-2.00\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/statements").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(mine));
    }

    @Test
    void getOwnStatementAndStatus() throws Exception {
        String id = upload(ALICE, CSV);

        mockMvc.perform(get("/api/statements/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.format").value("CSV"));
        mockMvc.perform(get("/api/statements/" + id + "/status").header("Authorization", bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void otherUsersStatementIsNotFound() throws Exception {
        String id = upload(ALICE, CSV);

        mockMvc.perform(get("/api/statements/" + id).header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/statements/" + id + "/status").header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesFileAndRow() throws Exception {
        String id = upload(ALICE, CSV);
        String key = statementRepository.findById(UUID.fromString(id)).orElseThrow().getStorageKey();

        mockMvc.perform(delete("/api/statements/" + id).header("Authorization", bearer(ALICE)))
                .andExpect(status().isNoContent());
        verify(storage).delete(key);
        assertThat(statementRepository.count()).isZero();
    }

    @Test
    void deletingOtherUsersStatementIsNotFound() throws Exception {
        String id = upload(ALICE, CSV);

        mockMvc.perform(delete("/api/statements/" + id).header("Authorization", bearer(BOB)))
                .andExpect(status().isNotFound());
        verify(storage, never()).delete(anyString());
        assertThat(statementRepository.count()).isEqualTo(1);
    }
}
