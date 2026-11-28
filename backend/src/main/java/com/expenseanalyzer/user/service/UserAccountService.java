package com.expenseanalyzer.user.service;

import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.model.UserAccount;
import com.expenseanalyzer.user.repository.UserAccountRepository;
import com.expenseanalyzer.user.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The user's own account numbers. Numbers are never logged. */
@Service
@RequiredArgsConstructor
public class UserAccountService {

    /** Digits, optionally masked with X or *, as printed on statements; the last four must be digits. */
    private static final Pattern NUMBER = Pattern.compile("[0-9X*]{0,46}[0-9]{4}");

    private final UserAccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserAccount> list(String email) {
        return accountRepository.findByUserIdOrderByCreatedAt(currentUser(email).getId());
    }

    @Transactional
    public UserAccount add(String email, String accountNumber, String bankName) {
        return add(currentUser(email).getId(), accountNumber, bankName);
    }

    /** Adds the account unless the user already has it. */
    @Transactional
    public UserAccount add(UUID userId, String accountNumber, String bankName) {
        String number = normalise(accountNumber);
        if (bankName != null && bankName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank name is too long");
        }
        UserAccount a = new UserAccount();
        a.setUserId(userId);
        a.setAccountNumber(number);
        a.setBankName(bankName == null || bankName.isBlank() ? null : bankName.trim());
        return accountRepository.existsByUserIdAndAccountNumber(userId, number) ? a : accountRepository.save(a);
    }

    /** @return false if the account does not exist or belongs to someone else */
    @Transactional
    public boolean delete(String email, UUID id) {
        return accountRepository.findByIdAndUserId(id, currentUser(email).getId()).map(a -> {
            accountRepository.delete(a);
            return true;
        }).orElse(false);
    }

    /** Upper-cased with spaces and dashes removed; 400 unless it looks like an account number. */
    public static String normalise(String accountNumber) {
        String n = accountNumber == null ? "" : accountNumber.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!NUMBER.matcher(n).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the account number, at least its last 4 digits");
        }
        return n;
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
