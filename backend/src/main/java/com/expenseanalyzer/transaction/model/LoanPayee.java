package com.expenseanalyzer.transaction.model;

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

/** A lender's default loan: the user's "Loan EMI" payments to this payee link to it. At most one per (user, payee). */
@Entity
@Table(name = "loan_payees")
@IdClass(LoanPayee.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class LoanPayee {

    public record Key(UUID userId, String payeeKey) implements Serializable {
    }

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "payee_key", nullable = false, updatable = false, length = 100)
    private String payeeKey;

    @Column(name = "loan_id", nullable = false)
    private UUID loanId;

    public LoanPayee(UUID userId, String payeeKey, UUID loanId) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.loanId = loanId;
    }
}
