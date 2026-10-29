package com.expenseanalyzer.audit;

import com.expenseanalyzer.audit.model.AuditAction;
import com.expenseanalyzer.audit.model.AuditLog;
import com.expenseanalyzer.audit.repository.AuditLogRepository;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;
import com.expenseanalyzer.messaging.repository.OutboxEventRepository;
import com.expenseanalyzer.observability.CorrelationIdFilter;
import com.expenseanalyzer.statement.repository.StatementRepository;
import com.expenseanalyzer.statement.service.StatementStorage;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuditLogTest {

    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private StatementRepository statementRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PreferenceRepository preferenceRepository;
    @MockitoBean
    private StatementStorage storage;

    private MockMvc mockMvc;
    private User alice;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(CorrelationIdFilter.class))
                .apply(springSecurity()).build();
        alice = new User(EMAIL, passwordEncoder.encode(PASSWORD));
        alice.setId(UUID.randomUUID());
        alice.setRole(UserRole.USER);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(alice));
    }

    @AfterEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        statementRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    private String bearer() {
        return "Bearer " + jwtTokenProvider.generateToken(new UsernamePasswordAuthenticationToken(EMAIL, null));
    }

    private List<AuditLog> logs(AuditAction action) {
        return auditLogRepository.findAll().stream().filter(l -> l.getAction() == action).toList();
    }

    private String upload() throws Exception {
        String body = mockMvc.perform(multipart("/api/statements")
                        .file(new MockMultipartFile("file", "salary-alice.csv", "text/csv",
                                "Date,Description,Amount\n2026-01-02,Coffee,-4.50\n".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer())
                        .header("X-Correlation-ID", "corr-audit-1"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void uploadIsAuditedWithActorResourceAndCorrelationId() throws Exception {
        String id = upload();

        AuditLog log = logs(AuditAction.STATEMENT_UPLOADED).getFirst();
        assertThat(log.getUserId()).isEqualTo(alice.getId());
        assertThat(log.getResourceType()).isEqualTo("STATEMENT");
        assertThat(log.getResourceId()).isEqualTo(UUID.fromString(id));
        assertThat(log.getCorrelationId()).isEqualTo("corr-audit-1");
        assertThat(log.getOccurredAt()).isNotNull();
    }

    @Test
    void statementAccessAndDeletionAreAudited() throws Exception {
        String id = upload();

        mockMvc.perform(get("/api/statements/" + id).header("Authorization", bearer())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/statements/" + id).header("Authorization", bearer())).andExpect(status().isNoContent());

        assertThat(logs(AuditAction.STATEMENT_ACCESSED)).hasSize(1);
        assertThat(logs(AuditAction.STATEMENT_DELETED)).singleElement()
                .satisfies(l -> assertThat(l.getResourceId()).isEqualTo(UUID.fromString(id)));
    }

    @Test
    void accessToMissingOrForeignStatementIsNotAuditedAsAccess() throws Exception {
        mockMvc.perform(get("/api/statements/" + UUID.randomUUID()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        assertThat(logs(AuditAction.STATEMENT_ACCESSED)).isEmpty();
    }

    @Test
    void successfulAndFailedLoginsAreAudited() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"whatever-123\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(logs(AuditAction.AUTH_LOGIN_SUCCEEDED)).singleElement()
                .satisfies(l -> assertThat(l.getUserId()).isEqualTo(alice.getId()));
        assertThat(logs(AuditAction.AUTH_LOGIN_FAILED)).extracting(AuditLog::getUserId)
                .containsExactlyInAnyOrder(alice.getId(), null);
    }

    @Test
    void registrationIsAudited() throws Exception {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"a-long-password\"}"))
                .andExpect(status().isOk());

        assertThat(logs(AuditAction.AUTH_REGISTERED)).singleElement()
                .satisfies(l -> assertThat(l.getUserId()).isNotNull());
    }

    @Test
    void auditRecordsContainNoEmailsOrFileNames() throws Exception {
        upload();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"wrong-password\"}"));

        assertThat(auditLogRepository.findAll()).isNotEmpty().allSatisfy(l ->
                assertThat(l.toString()).doesNotContain(EMAIL, "salary-alice", "nobody@"));
    }
}
