package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.merchant.dto.MerchantNameItem;
import com.expenseanalyzer.merchant.dto.MerchantNamesResponse;
import com.expenseanalyzer.merchant.model.HiddenMerchantSuggestion;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.MerchantSuggestionReport;
import com.expenseanalyzer.merchant.model.MerchantSuggestionVote;
import com.expenseanalyzer.merchant.model.SharingStrike;
import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import com.expenseanalyzer.merchant.repository.HiddenMerchantSuggestionRepository;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionReportRepository;
import com.expenseanalyzer.merchant.repository.MerchantSuggestionVoteRepository;
import com.expenseanalyzer.merchant.repository.SharingSettingsRepository;
import com.expenseanalyzer.merchant.repository.SharingSettingsRepository.Settings;
import com.expenseanalyzer.merchant.repository.SharingStrikeRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository.PayeeRow;
import com.expenseanalyzer.user.repository.UserRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Account-level sharing of the user's merchant names, per-name opt-out, and moderation of shared suggestions.
 * Invariant kept by {@link #sync}: a user has a suggestion vote for a payee exactly when their sharing is on (and not
 * revoked), that name is not opted out, and it is eligible (see {@link NotShareableReason}). Nothing is logged.
 */
@Service
@RequiredArgsConstructor
public class MerchantNameService {

    /** Distinct reporters that hide a suggestion for everyone; also the strikes that revoke a user's sharing. */
    static final int THRESHOLD = 2;

    private final UserRepository userRepository;
    private final SharingSettingsRepository settingsRepository;
    private final UserMerchantAliasRepository aliasRepository;
    private final MerchantSuggestionVoteRepository voteRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantSuggestionReportRepository reportRepository;
    private final HiddenMerchantSuggestionRepository hiddenRepository;
    private final SharingStrikeRepository strikeRepository;
    private final MerchantService merchantService;

    /** One alias with what eligibility needs; {@code reason} is null when it may be shared. */
    private record View(UserMerchantAlias alias, Merchant merchant, long transactionCount, NotShareableReason reason) {
    }

    @Transactional(readOnly = true)
    public MerchantNamesResponse list(String email) {
        return response(userId(email));
    }

    /** Turns account-level sharing on (votes for every eligible, not opted-out name) or off (deletes all votes). */
    @Transactional
    public MerchantNamesResponse setSharing(String email, boolean enabled) {
        UUID userId = userId(email);
        if (enabled && settingsRepository.settings(userId).sharingRevoked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sharing has been turned off for this account");
        }
        settingsRepository.setSharing(userId, enabled);
        sync(userId, views(userId));
        return response(userId);
    }

    /** Opts one of the caller's names out of (or back into) sharing. Not the caller's: 404; revoked: 403. */
    @Transactional
    public MerchantNameItem setAliasSharing(String email, UUID aliasId, boolean shared) {
        UUID userId = userId(email);
        UserMerchantAlias alias = aliasRepository.findByUserIdAndPublicId(userId, aliasId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (settingsRepository.settings(userId).sharingRevoked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sharing has been turned off for this account");
        }
        alias.setShareOptOut(!shared);
        aliasRepository.save(alias);
        sync(userId, views(userId));
        return response(userId).items().stream().filter(i -> i.id().equals(aliasId)).findFirst().orElseThrow();
    }

    /**
     * After the user named a payee: while sharing is on, {@code share} (default true) sets that name's opt-out; while
     * off it is ignored. Then re-syncs the user's votes.
     */
    @Transactional
    public void afterNaming(UUID userId, String payeeKey, Boolean share) {
        if (settingsRepository.settings(userId).sharing()) {
            aliasRepository.findById(new UserMerchantAlias.Key(userId, payeeKey)).ifPresent(alias -> {
                alias.setShareOptOut(Boolean.FALSE.equals(share));
                aliasRepository.save(alias);
            });
        }
        sync(userId, views(userId));
    }

    /**
     * Records the user's report of a suggestion they were shown. Once {@link #THRESHOLD} distinct users reported the
     * same pair it is hidden for everyone, each of its voters gets a strike, and voters with {@link #THRESHOLD}
     * strikes lose sharing (flag set, sharing off, all votes deleted). The account itself is never touched.
     */
    @Transactional
    public void report(UUID userId, String payeeKey, UUID merchantId) {
        if (!reportRepository.existsById(new MerchantSuggestionReport.Key(userId, payeeKey, merchantId))) {
            reportRepository.save(new MerchantSuggestionReport(userId, payeeKey, merchantId));
        }
        // ponytail: two concurrent reports can each count 1 and leave the pair visible to others; lock the pair if
        // that ever matters (both reporters stop seeing it either way).
        if (reportRepository.reporters(payeeKey, merchantId) < THRESHOLD
                || hiddenRepository.existsById(new HiddenMerchantSuggestion.Key(payeeKey, merchantId))) {
            return;
        }
        hiddenRepository.save(new HiddenMerchantSuggestion(payeeKey, merchantId));
        List<UUID> voters = voteRepository.voters(payeeKey, merchantId);
        if (voters.isEmpty()) {
            return;
        }
        for (UUID voter : voters) {
            if (!strikeRepository.existsById(new SharingStrike.Key(voter, payeeKey, merchantId))) {
                strikeRepository.save(new SharingStrike(voter, payeeKey, merchantId));
            }
        }
        List<UUID> revoked = strikeRepository.withAtLeast(voters, THRESHOLD);
        if (!revoked.isEmpty()) {
            settingsRepository.revoke(revoked);
            voteRepository.withdrawAll(revoked);
        }
    }

    private MerchantNamesResponse response(UUID userId) {
        Settings settings = settingsRepository.settings(userId);
        List<View> views = views(userId);
        Map<String, UUID> votes = votes(userId);
        Set<String> reported = views.isEmpty() ? Set.of() : reportRepository.findByPayeeKeyIn(
                        views.stream().map(v -> v.alias().getPayeeKey()).toList()).stream()
                .map(r -> pair(r.getPayeeKey(), r.getMerchantId())).collect(Collectors.toSet());
        List<MerchantNameItem> items = views.stream().map(v -> {
            UserMerchantAlias a = v.alias();
            NotShareableReason reason = v.reason();
            return new MerchantNameItem(a.getPublicId(), a.getDisplayName(), a.getPayeeKey(), v.transactionCount(),
                    a.getMerchantId().equals(votes.get(a.getPayeeKey())), a.isShareOptOut(), reason == null,
                    reason == null ? null : reason.name(), reported.contains(pair(a.getPayeeKey(), a.getMerchantId())));
        }).sorted(Comparator.comparing((MerchantNameItem i) -> i.name().toLowerCase(Locale.ROOT))
                .thenComparing(MerchantNameItem::payee)).toList();
        return new MerchantNamesResponse(settings.shareMerchantNames(), settings.sharingRevoked(), items);
    }

    /**
     * The user's aliases with their eligibility: every one of the user's payments to the payee (at least one) must be
     * shareable per {@link MerchantService#payeeReason}, and the shared merchant name must pass
     * {@link MerchantService#nameReason}. Three queries, however many aliases or transactions.
     */
    private List<View> views(UUID userId) {
        List<UserMerchantAlias> aliases = aliasRepository.findByUserId(userId);
        if (aliases.isEmpty()) {
            return List.of();
        }
        Map<UUID, Merchant> merchants = merchantRepository.findAllById(
                        aliases.stream().map(UserMerchantAlias::getMerchantId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Merchant::getId, Function.identity()));
        Map<String, List<PayeeRow>> rowsByKey = aliasRepository.payeeRows(userId).stream()
                .filter(r -> PayeeKey.of(r.rawDescription(), r.normalizedDescription()) != null)
                .collect(Collectors.groupingBy(r -> PayeeKey.of(r.rawDescription(), r.normalizedDescription())));
        return aliases.stream().map(a -> {
            List<PayeeRow> rows = rowsByKey.getOrDefault(a.getPayeeKey(), List.of());
            Merchant merchant = merchants.get(a.getMerchantId());
            NotShareableReason reason = rows.isEmpty() ? NotShareableReason.NOT_UPI_MERCHANT
                    : rows.stream().map(r -> MerchantService.payeeReason(r.rawDescription(), r.personId()))
                            .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            if (reason == null) {
                reason = MerchantService.nameReason(merchant == null ? null : merchant.getCanonicalName());
            }
            return new View(a, merchant, rows.size(), reason);
        }).toList();
    }

    /** Makes the user's votes match the invariant in the class comment. */
    private void sync(UUID userId, List<View> views) {
        boolean sharing = settingsRepository.settings(userId).sharing();
        Map<String, UUID> current = votes(userId);
        Set<String> keep = new HashSet<>();
        for (View v : views) {
            String key = v.alias().getPayeeKey();
            if (sharing && !v.alias().isShareOptOut() && v.reason() == null) {
                keep.add(key);
                if (!v.merchant().getId().equals(current.get(key))) {
                    merchantService.vote(userId, key, v.merchant());
                }
            }
        }
        Set<String> drop = new HashSet<>(current.keySet());
        drop.removeAll(keep);
        if (!drop.isEmpty()) {
            voteRepository.withdraw(userId, drop);
        }
    }

    private Map<String, UUID> votes(UUID userId) {
        return voteRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(MerchantSuggestionVote::getPayeeKey, MerchantSuggestionVote::getMerchantId));
    }

    private static String pair(String payeeKey, UUID merchantId) {
        return payeeKey + "\n" + merchantId;
    }

    private UUID userId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
}
