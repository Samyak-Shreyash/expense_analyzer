package com.expenseanalyzer.auth;

import com.expenseanalyzer.auth.config.SecurityConfiguration;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.expenseanalyzer.user.service.UserService;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
@SpringBootTest
@ContextConfiguration(classes = SecurityConfiguration.class)
class TestConfig {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PreferenceRepository preferenceRepository;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private SecurityFilterChain securityFilterChain;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthenticationProvider authenticationProvider;

}
