package com.expenseanalyzer.security;

import com.expenseanalyzer.auth.repository.UserRepository;
import com.expenseanalyzer.user.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest
class SecurityConfigurationTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void testAuthenticationManagerExists() {
        // Just verify the bean exists
    }

}
