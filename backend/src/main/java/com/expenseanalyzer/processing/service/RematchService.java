package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.transaction.model.Transaction;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.transaction.service.LoanService;
import com.expenseanalyzer.transaction.service.ReversalService;
import com.expenseanalyzer.transaction.service.TransferService;
import com.expenseanalyzer.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Re-runs enrichment on a user's unlabelled rows, then transfer and reversal matching. Logs counts only. */
@Service
public class RematchService {

    private static final Logger log = LoggerFactory.getLogger(RematchService.class);

    private final TransactionRepository transactionRepository;
    private final TransactionEnricher enricher;
    private final UserRepository userRepository;
    private final TransferService transferService;
    private final ReversalService reversalService;
    private final EffectiveCategoryResolver categoryResolver;
    private final LoanService loanService;

    public RematchService(TransactionRepository transactionRepository, TransactionEnricher enricher, UserRepository userRepository,
                          TransferService transferService, ReversalService reversalService,
                          EffectiveCategoryResolver categoryResolver, LoanService loanService) {
        this.transactionRepository = transactionRepository;
        this.enricher = enricher;
        this.userRepository = userRepository;
        this.transferService = transferService;
        this.reversalService = reversalService;
        this.categoryResolver = categoryResolver;
        this.loanService = loanService;
    }

    @Transactional
    public void rematch(String email) {
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
        List<Transaction> candidates = transactionRepository.findByUserId(userId).stream()
                .filter(t -> !t.isDuplicate())
                .filter(t -> t.getMerchantId() == null && t.getPersonId() == null && t.getCategoryId() == null)
                .toList();
        enricher.enrich(candidates, userId);
        enricher.applyKeywordCategories(userId, candidates);
        categoryResolver.markSelfAsTransfers(userId, candidates);
        Set<UUID> loans = loanService.autoLink(userId, candidates);
        transactionRepository.saveAll(candidates);
        loanService.recompute(loans);
        transferService.match(userId);
        reversalService.match(userId);
        log.info("Rematched transactions: candidates={}", candidates.size());
    }
}
