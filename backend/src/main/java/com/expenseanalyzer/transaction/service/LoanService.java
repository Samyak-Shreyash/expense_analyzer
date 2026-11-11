package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.category.service.CategoryService;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver.Resolution;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.merchant.service.PayeeKey;
import com.expenseanalyzer.transaction.model.LoanPayee;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.LoanPayeeRepository;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Loans and their EMIs. A loan is a CREDIT whose effective category is "Loan"; an EMI is a DEBIT in "Loan EMI".
 * Linking an EMI makes its loan the lender's (payee's) default, so the lender's other and future EMIs link too.
 * A loan's EMIs repay its principal in date order; what is paid beyond it is interest, kept on each EMI so analytics
 * can count it as spending. Closing a loan stops its lender's later payments being linked or categorised as EMIs.
 */
@Service
@RequiredArgsConstructor
public class LoanService {

    private final TransactionRepository transactionRepository;
    private final LoanPayeeRepository loanPayeeRepository;
    private final EffectiveCategoryResolver categoryResolver;
    private final PersonRepository personRepository;
    private final UserMerchantCategoryRepository merchantCategoryRepository;

    /**
     * Links the EMI, and the lender's other unlinked EMIs, to the loan and makes it the lender's default; a null
     * {@code loanId} unlinks this EMI only. Not an EMI, not a loan, or a closed loan: 400. Another user's loan: 404.
     */
    @Transactional
    public void link(UUID userId, Transaction emi, UUID loanId) {
        Set<UUID> affected = new HashSet<>();
        Instant now = Instant.now();
        if (loanId == null) {
            affected.add(emi.getLoanId());
            emi.setLoanId(null);
            emi.setLoanInterestPaise(null);
            emi.setUpdatedAt(now);
            transactionRepository.save(emi);
            recompute(affected);
            return;
        }
        Resolution resolution = categoryResolver.forUser(userId);
        if (!isEmi(emi, resolution)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only a Loan EMI payment can be linked to a loan");
        }
        Transaction loan = transactionRepository.findByIdAndUserId(loanId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!isLoan(loan, resolution)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick money in categorised as Loan");
        }
        if (loan.isLoanClosed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This loan is closed");
        }
        String key = payee(emi);
        List<Transaction> rows = new ArrayList<>(List.of(emi));
        if (key != null) {
            loanPayeeRepository.save(new LoanPayee(userId, key, loanId));
            transactionRepository.findByUserId(userId).stream()
                    .filter(t -> !t.getId().equals(emi.getId()) && t.getLoanId() == null && key.equals(payee(t))
                            && isEmi(t, resolution))
                    .forEach(rows::add);
        }
        affected.add(loanId);
        for (Transaction t : rows) {
            affected.add(t.getLoanId());
            t.setLoanId(loanId);
            t.setUpdatedAt(now);
        }
        transactionRepository.saveAll(rows);
        recompute(affected);
    }

    /**
     * Links new EMIs whose lender has a default loan. Call before saving them, then {@link #recompute} the returned
     * loans once they are saved.
     */
    public Set<UUID> autoLink(UUID userId, List<Transaction> fresh) {
        Map<String, UUID> defaults = loanPayeeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(LoanPayee::getPayeeKey, LoanPayee::getLoanId));
        if (defaults.isEmpty()) {
            return Set.of();
        }
        Resolution resolution = categoryResolver.forUser(userId);
        Set<UUID> loans = new HashSet<>();
        for (Transaction t : fresh) {
            String key = payee(t);
            UUID loanId = key == null ? null : defaults.get(key);
            if (loanId != null && t.getLoanId() == null && isEmi(t, resolution)) {
                t.setLoanId(loanId);
                loans.add(loanId);
            }
        }
        return loans;
    }

    /** Re-splits each loan's EMIs into principal and interest. Null ids are skipped. */
    @Transactional
    public void recompute(Set<UUID> loanIds) {
        transactionRepository.flush();
        for (UUID loanId : loanIds) {
            if (loanId == null) {
                continue;
            }
            BigDecimal principal = transactionRepository.findById(loanId).map(Transaction::getAmountCents)
                    .orElse(BigDecimal.ZERO);
            BigDecimal paid = BigDecimal.ZERO;
            List<Transaction> emis = transactionRepository.findByLoanIdOrderByDateAscCreatedAtAsc(loanId);
            for (Transaction t : emis) {
                BigDecimal before = paid;
                paid = paid.add(t.getAmountCents());
                BigDecimal interest = paid.subtract(before.max(principal));
                t.setLoanInterestPaise(interest.signum() > 0 ? interest : null);
            }
            transactionRepository.saveAll(emis);
        }
    }

    /**
     * Closes or reopens a loan. Closing keeps its EMIs as "Loan EMI" (set on each row), drops the lender's default
     * loan and, where the lender's own default category is "Loan EMI", clears it, so later payments are neither
     * linked nor categorised as EMIs. Reopening makes it its lenders' default loan again. Not a loan: 400.
     */
    @Transactional
    public void setClosed(UUID userId, Transaction loan, boolean closed) {
        Resolution resolution = categoryResolver.forUser(userId);
        if (!isLoan(loan, resolution)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only money in categorised as Loan is a loan");
        }
        List<Transaction> emis = transactionRepository.findByLoanIdOrderByDateAscCreatedAtAsc(loan.getId());
        if (closed) {
            Category emiCategory = resolution.named(CategoryService.LOAN_EMI);
            if (emiCategory != null) {
                emis.stream().filter(t -> t.getCategoryId() == null).forEach(t -> t.setCategoryId(emiCategory.getId()));
                transactionRepository.saveAll(emis);
                clearLenderCategory(userId, emis, emiCategory.getId());
            }
            loanPayeeRepository.deleteAll(loanPayeeRepository.findByLoanId(loan.getId()));
        } else {
            emis.stream().map(LoanService::payee).filter(Objects::nonNull).distinct()
                    .forEach(key -> loanPayeeRepository.save(new LoanPayee(userId, key, loan.getId())));
        }
        loan.setLoanClosed(closed);
        loan.setUpdatedAt(Instant.now());
        transactionRepository.save(loan);
    }

    private void clearLenderCategory(UUID userId, List<Transaction> emis, UUID emiCategoryId) {
        emis.stream().map(Transaction::getPersonId).filter(Objects::nonNull).distinct()
                .flatMap(id -> personRepository.findByIdAndUserId(id, userId).stream())
                .filter(p -> emiCategoryId.equals(p.getCategoryId()))
                .forEach(p -> {
                    p.setCategoryId(null);
                    personRepository.save(p);
                });
        emis.stream().map(Transaction::getMerchantId).filter(Objects::nonNull).distinct()
                .flatMap(id -> merchantCategoryRepository.findByUserIdAndMerchantId(userId, id).stream())
                .filter(m -> emiCategoryId.equals(m.getCategoryId()))
                .forEach(m -> {
                    m.setCategoryId(null);
                    merchantCategoryRepository.save(m);
                });
    }

    static boolean isEmi(Transaction t, Resolution resolution) {
        Category c = resolution.resolve(t, null);
        return "DEBIT".equals(t.getType()) && c != null && c == resolution.named(CategoryService.LOAN_EMI);
    }

    static boolean isLoan(Transaction t, Resolution resolution) {
        Category c = resolution.resolve(t, null);
        return "CREDIT".equals(t.getType()) && c != null && c == resolution.named(CategoryService.LOAN);
    }

    private static String payee(Transaction t) {
        return PayeeKey.of(t.getRawDescription(), t.getNormalizedDescription());
    }
}
