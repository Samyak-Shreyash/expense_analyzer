package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.dto.JwtTokenProvider;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class SecurityConfigurationTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void testAuthenticationManagerExists() {
        // Just verify the bean exists
    }

    @Test
    void generatesJwtWithConfiguredSecret() {
        String token = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken("user@example.com", "password"));

        assertNotNull(token);
    }

}
