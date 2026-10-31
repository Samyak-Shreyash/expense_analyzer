package com.expenseanalyzer.merchant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A payee name (upper-cased, as parsed from UPI transfers) that belongs to one of the user's people. Several names can
 * group under one person; at most one row per (user, payee).
 */
@Entity
@Table(name = "person_payees")
@IdClass(PersonPayee.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class PersonPayee {

    public record Key(UUID userId, String payeeKey) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "payee_key", nullable = false, updatable = false, length = 100)
    private String payeeKey;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    public PersonPayee(UUID userId, String payeeKey, UUID personId) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.personId = personId;
    }
}
