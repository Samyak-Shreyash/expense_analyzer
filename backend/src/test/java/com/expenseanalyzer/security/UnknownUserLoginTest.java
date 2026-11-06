package com.expenseanalyzer.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.expenseanalyzer.auth.config.SecurityConfiguration;
import com.expenseanalyzer.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class UnknownUserLoginTest {

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        Mockito.when(userRepository.findByEmail("nobody@example.com"))
                .thenReturn(Optional.empty());

        SecurityConfiguration config = new SecurityConfiguration(userRepository);

        assertThatThrownBy(() -> config.userDetailsService().loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
