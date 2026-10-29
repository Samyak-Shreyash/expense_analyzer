package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import com.expenseanalyzer.merchant.model.MerchantSuggestionVote;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final UserMerchantAliasRepository aliasRepository;
    private final MerchantSuggestionVoteRepository voteRepository;

    /**
     * Find-or-create by canonical name, ignoring case (the first-created spelling is kept). A concurrent create loses on the unique constraint and the
     * caller's transaction is retried, after which the merchant is found.
     */
    @Transactional
    public Merchant resolve(MerchantMatch match) {
        return merchantRepository.findFirstByCanonicalNameIgnoreCaseOrderByCreatedAtAsc(match.canonicalName())
                .orElseGet(() -> merchantRepository.save(new Merchant(match.canonicalName(), match.categoryHint())));
    }

    /** The user's merchant names by payee key (see {@link PayeeKey}). */
    @Transactional(readOnly = true)
    public Map<String, UUID> aliases(UUID userId) {
        return aliasRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMerchantAlias::getPayeeKey, UserMerchantAlias::getMerchantId));
    }

    /** Creates or repoints the user's alias for a payee key. */
    @Transactional
    public void saveAlias(UUID userId, String payeeKey, UUID merchantId) {
        UserMerchantAlias alias = aliasRepository.findById(new UserMerchantAlias.Key(userId, payeeKey))
                .orElseGet(() -> new UserMerchantAlias(userId, payeeKey, merchantId));
        alias.setMerchantId(merchantId);
        aliasRepository.save(alias);
    }

    /** Records the user's opt-in vote for a payee key (replacing any earlier one). */
    @Transactional
    public void vote(UUID userId, String payeeKey, UUID merchantId) {
        voteRepository.withdraw(userId, payeeKey);
        voteRepository.save(new MerchantSuggestionVote(userId, payeeKey, merchantId));
    }

    @Transactional
    public void withdrawVote(UUID userId, String payeeKey) {
        voteRepository.withdraw(userId, payeeKey);
    }

    /**
     * For each of the given payee keys with a merchant suggested by at least two other users, the merchant with the
     * most distinct voters (ties: earliest vote). One query; keys without a suggestion are absent.
     */
    @Transactional(readOnly = true)
    public Map<String, UUID> suggestions(UUID userId, Collection<String> payeeKeys) {
        if (payeeKeys.isEmpty()) {
            return Map.of();
        }
        return voteRepository.suggestions(payeeKeys, userId).stream()
                .collect(Collectors.toMap(MerchantSuggestionVoteRepository.Tally::payeeKey, t -> t, (a, b) ->
                        a.voters() != b.voters() ? (a.voters() > b.voters() ? a : b)
                                : (a.first().isAfter(b.first()) ? b : a)))
                .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().merchantId()));
    }
}
