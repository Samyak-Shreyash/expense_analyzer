package com.expenseanalyzer.auth.repository;

import com.expenseanalyzer.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
}
