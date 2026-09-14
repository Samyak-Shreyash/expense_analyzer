package com.expenseanalyzer.auth.controller;

import com.expenseanalyzer.auth.dto.LoginRequest;
import com.expenseanalyzer.auth.dto.LoginResponse;
import com.expenseanalyzer.auth.dto.RegisterRequest;
import com.expenseanalyzer.auth.dto.RegisterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private com.expenseanalyzer.auth.service.UserService userService;

    private RegisterResponse mockRegisterResponse;
    private LoginResponse mockLoginResponse;

    @BeforeEach
    void setUp() {
        // Setup mock responses
        mockRegisterResponse = new RegisterResponse(
                UUID.randomUUID(), "test@example.com", "Test User");
        mockLoginResponse = new LoginResponse(
                "fake-jwt-token-" + UUID.randomUUID().toString(),
                UUID.randomUUID(), "test@example.com");
    }

    @Test
    void registerUser_ShouldReturn200WithRegisterResponse() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("password123");

        // Mock the service call
        when(userService.registerUser(request)).thenReturn(mockRegisterResponse);

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"Test User\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    void loginUser_ShouldReturn200WithLoginResponse() throws Exception {
        // Given
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");

        // Mock the service call
        when(userService.loginUser(request)).thenReturn(mockLoginResponse);

        // When & Then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwtToken").exists())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void registerUser_ShouldReturn400WhenEmailIsMissing() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setFullName("Test User");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"fullName\":\"Test User\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenPasswordIsMissing() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"Test User\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenPasswordIsTooShort() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword("short");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"Test User\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenEmailIsInvalid() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("invalid-email-format");
        request.setFullName("Test User");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email-format\",\"fullName\":\"Test User\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenEmailIsEmpty() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("");
        request.setFullName("Test User");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"fullName\":\"Test User\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenFullNameIsMissing() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenFullNameIsTooLong() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("This is a very long name that exceeds the maximum allowed length of 255 characters for validation purposes");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"This is a very long name that exceeds the maximum allowed length of 255 characters for validation purposes\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenFullNameIsEmpty() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenFullNameIsNull() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName(null);
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":null,\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenPasswordIsNull() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Test User");
        request.setPassword(null);

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":\"Test User\",\"password\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenEmailIsNull() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail(null);
        request.setFullName("Test User");
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":null,\"fullName\":\"Test User\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerUser_ShouldReturn400WhenFullNameIsNull() throws Exception {
        // Given
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName(null);
        request.setPassword("password123");

        // When & Then
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"fullName\":null,\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }
}
