package com.expenseanalyzer.auth;

import com.expenseanalyzer.auth.config.SecurityConfiguration;
import com.expenseanalyzer.user.model.UserRole;
import com.expenseanalyzer.user.repository.UserRepository;
import com.expenseanalyzer.user.service.UserService;
import com.expenseanalyzer.auth.service.AuthService;
import com.expenseanalyzer.user.repository.PreferenceRepository;
import com.expenseanalyzer.auth.dto.JwtTokenProvider;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private PreferenceRepository preferenceRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SecurityFilterChain securityFilterChain;

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    @Mock
    private AuthenticationProvider authenticationProvider;

}
