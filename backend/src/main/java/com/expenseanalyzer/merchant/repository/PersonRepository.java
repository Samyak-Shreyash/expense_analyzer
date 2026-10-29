package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.Person;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonRepository extends JpaRepository<Person, UUID> {

    List<Person> findByUserIdOrderByNameAsc(UUID userId);

    Optional<Person> findByIdAndUserId(UUID id, UUID userId);

    Optional<Person> findByUserIdAndNameIgnoreCase(UUID userId, String name);

    /** Detaches the category from every person (the FK also does this on Postgres; H2 tests have no FK). */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Person p SET p.categoryId = NULL WHERE p.categoryId = :categoryId")
    void clearCategory(UUID categoryId);
}
