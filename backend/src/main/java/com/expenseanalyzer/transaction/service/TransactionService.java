package com.expenseanalyzer.transaction.service;

import com.expenseanalyzer.transaction.dto.TransactionFilter;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owner-scoped transaction queries. The user always comes from the token, never from a parameter. */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<Transaction> list(String email, TransactionFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        UUID userId = currentUser(email).getId();
        Specification<Transaction> spec = (root, q, cb) -> cb.equal(root.get("userId"), userId);
        if (f.statementId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("statementId"), f.statementId()));
        }
        if (f.merchantId() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("merchantId"), f.merchantId()));
        }
        if (f.direction() != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), f.direction().name()));
        }
        if (f.from() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("date"),
                    f.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (f.to() != null) { // inclusive: before the start of the next day
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("date"),
                    f.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (f.minAmount() != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("amountCents"), f.minAmount().movePointRight(2)));
        }
        if (f.maxAmount() != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("amountCents"), f.maxAmount().movePointRight(2)));
        }
        return transactionRepository.findAll(spec, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> find(String email, UUID id) {
        return transactionRepository.findByIdAndUserId(id, currentUser(email).getId());
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
