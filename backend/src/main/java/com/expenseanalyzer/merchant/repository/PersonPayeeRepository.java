package com.expenseanalyzer.merchant.repository;

import com.expenseanalyzer.merchant.model.PersonPayee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonPayeeRepository extends JpaRepository<PersonPayee, PersonPayee.Key> {

    List<PersonPayee> findByUserId(UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PersonPayee p SET p.personId = :to WHERE p.personId = :from")
    void repoint(UUID from, UUID to);
}
