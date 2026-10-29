package com.expenseanalyzer.user.repository;

import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    long countByRole(UserRole role);

    @Modifying
    @Query("UPDATE User u SET u.categoriesSeeded = true WHERE u.id = :id AND u.categoriesSeeded = false")
    int claimCategorySeeding(@Param("id") UUID id);
}
