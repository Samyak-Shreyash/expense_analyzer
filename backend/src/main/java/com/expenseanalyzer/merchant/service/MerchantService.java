package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.model.MerchantSuggestionVote;
import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
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

    /** Only accounts at least this old count as suggestion voters. */
    static final Duration VOTER_MIN_AGE = Duration.ofDays(7);
    /** Letters (any script), spaces and & . ' - only, 2-60 chars; no "x.com" / "www.x" style domains. */
    private static final Pattern SUGGESTABLE = Pattern.compile("(?=.*\\p{L})(?!.*\\.\\p{L}{2})[\\p{L}\\p{M} &.'\\-]{2,60}");

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

    /** The user's aliases by payee key, with the spelling they typed. */
    @Transactional(readOnly = true)
    public Map<String, UserMerchantAlias> aliasEntries(UUID userId) {
        return aliasRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMerchantAlias::getPayeeKey, Function.identity()));
    }

    /** Creates or repoints the user's alias for a payee key, keeping the user's own spelling. */
    @Transactional
    public void saveAlias(UUID userId, String payeeKey, UUID merchantId, String displayName) {
        UserMerchantAlias alias = aliasRepository.findById(new UserMerchantAlias.Key(userId, payeeKey))
                .orElseGet(() -> new UserMerchantAlias(userId, payeeKey, merchantId, displayName));
        alias.setMerchantId(merchantId);
        alias.setDisplayName(displayName);
        aliasRepository.save(alias);
    }

    /** Drops the user's aliases and suggestion votes for the given payee keys. */
    @Transactional
    public void forget(UUID userId, Collection<String> payeeKeys) {
        if (!payeeKeys.isEmpty()) {
            aliasRepository.deleteByUserIdAndPayeeKeys(userId, payeeKeys);
            voteRepository.withdraw(userId, payeeKeys);
        }
    }

    /** True when a merchant name is safe to show to other users (letters of any script, spaces, & . ' -). */
    public static boolean suggestable(String name) {
        return name != null && SUGGESTABLE.matcher(name).matches();
    }

    /**
     * Records (or atomically replaces) the user's opt-in vote for a payee key. A merchant whose name is not
     * {@link #suggestable} is silently not recorded and any earlier vote is withdrawn.
     */
    @Transactional
    public void vote(UUID userId, String payeeKey, Merchant merchant) {
        if (!suggestable(merchant.getCanonicalName())) {
            withdrawVote(userId, payeeKey);
            return;
        }
        voteRepository.lockVoter(userId); // serialises this user's votes: no insert race on (user, payee)
        if (voteRepository.repoint(userId, payeeKey, merchant.getId(), Instant.now()) == 0) {
            voteRepository.save(new MerchantSuggestionVote(userId, payeeKey, merchant.getId()));
        }
    }

    @Transactional
    public void withdrawVote(UUID userId, String payeeKey) {
        voteRepository.withdraw(userId, List.of(payeeKey));
    }

    /**
     * For each of the given payee keys, the merchant suggested by strictly more distinct other users than any other
     * merchant, and by at least two; only accounts older than {@link #VOTER_MIN_AGE} count. Ties show nothing.
     * One query; keys without a suggestion are absent.
     */
    @Transactional(readOnly = true)
    public Map<String, UUID> suggestions(UUID userId, Collection<String> payeeKeys) {
        if (payeeKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, List<MerchantSuggestionVoteRepository.Tally>> byKey = voteRepository
                .tallies(payeeKeys, userId, Instant.now().minus(VOTER_MIN_AGE)).stream()
                .collect(Collectors.groupingBy(MerchantSuggestionVoteRepository.Tally::payeeKey));
        Map<String, UUID> out = new HashMap<>();
        byKey.forEach((key, tallies) -> {
            long top = tallies.stream().mapToLong(MerchantSuggestionVoteRepository.Tally::voters).max().orElse(0);
            List<MerchantSuggestionVoteRepository.Tally> winners = tallies.stream().filter(t -> t.voters() == top).toList();
            if (top >= 2 && winners.size() == 1) {
                out.put(key, winners.get(0).merchantId());
            }
        });
        return out;
    }
}
