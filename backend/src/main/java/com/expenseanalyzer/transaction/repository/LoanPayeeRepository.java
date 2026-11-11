package com.expenseanalyzer.transaction.repository;

import com.expenseanalyzer.transaction.model.LoanPayee;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanPayeeRepository extends JpaRepository<LoanPayee, LoanPayee.Key> {

    List<LoanPayee> findByUserId(UUID userId);

    List<LoanPayee> findByLoanId(UUID loanId);
}
