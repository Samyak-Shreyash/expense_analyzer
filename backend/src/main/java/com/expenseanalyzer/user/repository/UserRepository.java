package com.expenseanalyzer.user.repository;

import com.expenseanalyzer.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email address.
     *
     * @param email the user's email
     * @return optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Find user by ID.
     *
     * @param userId the user's UUID
     * @return optional containing the user if found
     */
    Optional<User> findById(UUID userId);
}
