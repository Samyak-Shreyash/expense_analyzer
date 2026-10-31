package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.category.model.UserMerchantCategory;
import com.expenseanalyzer.category.repository.UserMerchantCategoryRepository;
import com.expenseanalyzer.merchant.dto.MerchantNameItem;
import com.expenseanalyzer.merchant.dto.MerchantNamesResponse;
import com.expenseanalyzer.merchant.dto.SharingSettingsResponse;
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
import java.time.Duration;
import java.time.Instant;
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
    /** Strikes older than this no longer count toward revocation. */
    static final Duration STRIKE_TTL = Duration.ofDays(180);
    /** Suggestion reports one user may file per rolling 24 hours. */
    static final int REPORTS_PER_DAY = 10;

    private final UserRepository userRepository;
    private final SharingSettingsRepository settingsRepository;
    private final UserMerchantAliasRepository aliasRepository;
    private final MerchantSuggestionVoteRepository voteRepository;
    private final MerchantRepository merchantRepository;
    private final MerchantSuggestionReportRepository reportRepository;
    private final HiddenMerchantSuggestionRepository hiddenRepository;
    private final SharingStrikeRepository strikeRepository;
    private final MerchantService merchantService;
    private final UserMerchantCategoryRepository merchantCategoryRepository;

    /** One alias with what eligibility needs; {@code reason} is null when it may be shared. */
    private record View(UserMerchantAlias alias, Merchant merchant, long transactionCount, NotShareableReason reason) {
    }

    @Transactional
    public MerchantNamesResponse list(String email) {
        return response(userId(email));
    }

    /** Just the caller's account-level flags; loads no names or transactions. */
    @Transactional
    public SharingSettingsResponse settings(String email) {
        Settings s = effectiveSettings(userId(email));
        return new SharingSettingsResponse(s.shareMerchantNames(), s.sharingRevoked());
    }

    /** Turns account-level sharing on (votes for every eligible, not opted-out name) or off (deletes all votes). */
    @Transactional
    public MerchantNamesResponse setSharing(String email, boolean enabled) {
        UUID userId = userId(email);
        if (enabled && effectiveSettings(userId).sharingRevoked()) {
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
        if (effectiveSettings(userId).sharingRevoked()) {
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
        if (effectiveSettings(userId).sharing()) {
            aliasRepository.findById(new UserMerchantAlias.Key(userId, payeeKey)).ifPresent(alias -> {
                alias.setShareOptOut(Boolean.FALSE.equals(share));
                aliasRepository.save(alias);
            });
        }
        sync(userId, views(userId));
    }

    /**
     * Records the user's report of a suggestion they were shown (it is hidden for them at once). Reports are serialised
     * per merchant. The pair is hidden for everyone once its distinct reporters whose account was older than
     * {@link MerchantService#VOTER_MIN_AGE} when they filed reach max({@link #THRESHOLD}, its aged distinct voters). Then each voter
     * whose vote predates the pair's first report gets a strike, and voters with {@link #THRESHOLD} strikes from the
     * last {@link #STRIKE_TTL} lose sharing (flag set, sharing off, all votes deleted). The account is never touched.
     * More than {@link #REPORTS_PER_DAY} reports by one user in 24 hours: 429.
     */
    @Transactional
    public void report(UUID userId, String payeeKey, UUID merchantId) {
        Instant now = Instant.now();
        Instant joinedBefore = now.minus(MerchantService.VOTER_MIN_AGE);
        // ponytail: rate limit not atomic - concurrent reports from one user may exceed 10 slightly; harmless
        if (reportRepository.countByUserIdAndCreatedAtAfter(userId, now.minus(Duration.ofHours(24))) >= REPORTS_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many reports; try again later");
        }
        // Lock design: no path ever holds a users-row lock while waiting for a merchants-row lock. vote/saveAlias/sync/
        // setSharing lock only users; report locks only this merchant (its revoke UPDATEs of other users run while
        // holding it, which is safe because nothing holding a user lock ever waits on a merchant lock).
        reportRepository.lockMerchant(merchantId);
        if (!reportRepository.existsById(new MerchantSuggestionReport.Key(userId, payeeKey, merchantId))) {
            boolean aged = Boolean.TRUE.equals(reportRepository.accountOlderThan(userId, joinedBefore));
            reportRepository.saveAndFlush(new MerchantSuggestionReport(userId, payeeKey, merchantId, aged));
        }
        if (hiddenRepository.existsById(new HiddenMerchantSuggestion.Key(payeeKey, merchantId))) {
            return;
        }
        long reporters = reportRepository.agedReporters(payeeKey, merchantId);
        if (reporters < Math.max(THRESHOLD, voteRepository.agedVoters(payeeKey, merchantId, joinedBefore))) {
            return;
        }
        hiddenRepository.save(new HiddenMerchantSuggestion(payeeKey, merchantId));
        List<UUID> voters = voteRepository.votersBefore(payeeKey, merchantId,
                reportRepository.firstReportedAt(payeeKey, merchantId));
        if (voters.isEmpty()) {
            return;
        }
        for (UUID voter : voters) {
            if (!strikeRepository.existsById(new SharingStrike.Key(voter, payeeKey, merchantId))) {
                strikeRepository.save(new SharingStrike(voter, payeeKey, merchantId));
            }
        }
        strikeRepository.flush();
        List<UUID> revoked = strikeRepository.withAtLeast(voters, THRESHOLD, now.minus(STRIKE_TTL));
        if (!revoked.isEmpty()) {
            settingsRepository.revoke(revoked);
            voteRepository.withdrawAll(revoked);
        }
    }

    /**
     * The user's flags, where a revocation holds only while {@link #THRESHOLD} strikes from the last
     * {@link #STRIKE_TTL} remain; a lapsed one is cleared here (sharing stays off until the user turns it on).
     */
    private Settings effectiveSettings(UUID userId) {
        Settings s = settingsRepository.settings(userId);
        if (s.sharingRevoked() && strikeRepository.countByUserIdAndCreatedAtAfter(userId,
                Instant.now().minus(STRIKE_TTL)) < THRESHOLD) {
            settingsRepository.clearRevoked(userId);
            return new Settings(s.shareMerchantNames(), false);
        }
        return s;
    }

    private MerchantNamesResponse response(UUID userId) {
        Settings settings = effectiveSettings(userId);
        List<View> views = views(userId);
        Map<String, UUID> votes = votes(userId);
        // Only a pair the user votes for (or was struck for) may show as reported; never someone else's pair.
        Set<String> involved = new HashSet<>();
        votes.forEach((key, merchantId) -> involved.add(pair(key, merchantId)));
        strikeRepository.findByUserId(userId).forEach(s -> involved.add(pair(s.getPayeeKey(), s.getMerchantId())));
        Set<String> reported = views.isEmpty() ? Set.of() : reportRepository.findByPayeeKeyIn(
                        views.stream().map(v -> v.alias().getPayeeKey()).toList()).stream()
                .map(r -> pair(r.getPayeeKey(), r.getMerchantId())).filter(involved::contains)
                .collect(Collectors.toSet());
        Map<UUID, UserMerchantCategory> categories = merchantCategoryRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserMerchantCategory::getMerchantId, Function.identity()));
        List<MerchantNameItem> items = views.stream().map(v -> {
            UserMerchantAlias a = v.alias();
            NotShareableReason reason = v.reason();
            return new MerchantNameItem(a.getPublicId(), a.getDisplayName(), a.getPayeeKey(), v.transactionCount(),
                    a.getMerchantId().equals(votes.get(a.getPayeeKey())), a.isShareOptOut(), reason == null,
                    reason == null ? null : reason.name(), reported.contains(pair(a.getPayeeKey(), a.getMerchantId())),
                    a.getMerchantId(), category(categories, a.getMerchantId(), false),
                    category(categories, a.getMerchantId(), true));
        }).sorted(Comparator.comparing((MerchantNameItem i) -> i.name().toLowerCase(Locale.ROOT))
                .thenComparing(MerchantNameItem::payee)).toList();
        return new MerchantNamesResponse(settings.shareMerchantNames(), settings.sharingRevoked(), items);
    }

    private static UUID category(Map<UUID, UserMerchantCategory> categories, UUID merchantId, boolean credit) {
        UserMerchantCategory c = categories.get(merchantId);
        return c == null ? null : c.categoryFor(credit);
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
        boolean sharing = effectiveSettings(userId).sharing();
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
