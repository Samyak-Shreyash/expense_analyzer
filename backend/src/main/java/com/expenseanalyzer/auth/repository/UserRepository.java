package com.expenseanalyzer.auth.repository;

import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    long countByRole(UserRole role);
}
