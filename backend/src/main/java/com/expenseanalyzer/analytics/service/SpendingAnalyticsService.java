package com.expenseanalyzer.analytics.service;

import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CashFlow;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CategoryBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CategorySpend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MerchantBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MerchantSpend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MonthlyCashFlow;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MonthlyTrend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.NonSpendingBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.NonSpendingCategory;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.PeriodTotals;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.PersonSpend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.SpendingSummary;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.Trends;
import com.expenseanalyzer.category.model.Category;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.category.service.EffectiveCategoryResolver.Resolution;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.model.Person;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.repository.PersonRepository;
import com.expenseanalyzer.transaction.repository.TransactionRepository;
import com.expenseanalyzer.user.model.User;
import com.expenseanalyzer.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deterministic spending analytics (LLD sections 15-16), aggregated in the database.
 * <p>
 * Semantics: dates are inclusive UTC days. Inflow = all CREDITs (including refunds). "Spending" (summary outflow,
 * merchants, categories, trends) only counts transactions whose effective category counts as spending
 * (Uncategorized does); the cash-flow endpoint and summary inflow include everything. Groups are aggregated in SQL
 * by (category, person, merchant, type) and resolved to an effective category in memory.
 * Merchant/category "spend" = DEBITs minus CREDITs for a matched merchant (refunds net out); for transactions
 * without a merchant only DEBITs count, because unmatched CREDITs are mostly income. Groups whose net spend is
 * not positive are left out. Transfers between a user's own accounts are not detected yet.
 */
@Service
@RequiredArgsConstructor
public class SpendingAnalyticsService {

    public static final String CURRENCY = "INR";
    static final String UNMATCHED = "Unmatched";
    public static final String UNCATEGORIZED = "Uncategorized";
    private static final int MAX_RANGE_YEARS = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final PersonRepository personRepository;
    private final EffectiveCategoryResolver categoryResolver;

    private record Totals(BigDecimal inflowCents, BigDecimal outflowCents, long count,
                          BigDecimal nonSpendingCents) {
    }

    /** Per-merchant net spend in cents, before names are attached. */
    private record MerchantCents(UUID merchantId, BigDecimal spendCents, long count) {
    }

    /** One SQL group with its effective category resolved (null = Uncategorized). */
    private record Group(Category category, UUID personId, UUID merchantId, boolean credit, BigDecimal cents,
                         long count) {
        String categoryName() {
            return category == null ? UNCATEGORIZED : category.getName();
        }

        boolean spending() {
            return category == null || category.isCountsAsSpending();
        }
    }

    private record Net(BigDecimal cents, long count) {
    }

    @Transactional // not readOnly: first access seeds the user's default categories
    public SpendingSummary summary(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        UUID userId = userId(email);
        Resolution resolution = categoryResolver.forUser(userId);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        Totals cur = totals(userId, from, to, resolution);
        Totals prev = totals(userId, prevFrom, prevTo, resolution);
        PeriodTotals previous = new PeriodTotals(prevFrom, prevTo, money(prev.inflowCents()), money(prev.outflowCents()),
                money(prev.inflowCents().subtract(prev.outflowCents()).subtract(prev.nonSpendingCents())), prev.count(),
                money(prev.nonSpendingCents()));
        return new SpendingSummary(from, to, CURRENCY, money(cur.inflowCents()), money(cur.outflowCents()),
                money(cur.inflowCents().subtract(cur.outflowCents()).subtract(cur.nonSpendingCents())), cur.count(),
                previous, changePercent(cur.inflowCents(), prev.inflowCents()),
                changePercent(cur.outflowCents(), prev.outflowCents()), money(cur.nonSpendingCents()));
    }

    @Transactional // not readOnly: first access seeds the user's default categories
    public MerchantBreakdown merchants(String email, LocalDate from, LocalDate to, int limit) {
        validate(from, to);
        UUID userId = userId(email);
        List<Group> groups = groups(userId, from, to, categoryResolver.forUser(userId));
        // person transfers are not merchants
        List<MerchantCents> spend = net(groups, g -> g.personId() == null, Group::merchantId).entrySet().stream()
                .map(e -> new MerchantCents(e.getKey(), e.getValue().cents(), e.getValue().count()))
                .sorted(Comparator.comparing(MerchantCents::spendCents).reversed()
                        .thenComparing(m -> String.valueOf(m.merchantId())))
                .toList();
        BigDecimal total = spend.stream().map(MerchantCents::spendCents).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, Merchant> merchants = merchants(spend.stream().map(MerchantCents::merchantId).toList());
        List<MerchantSpend> items = spend.stream()
                .limit(limit)
                .map(m -> new MerchantSpend(m.merchantId(),
                        m.merchantId() == null ? UNMATCHED : merchants.get(m.merchantId()).getCanonicalName(),
                        money(m.spendCents()), share(m.spendCents(), total), m.count()))
                .toList();
        return new MerchantBreakdown(from, to, CURRENCY, money(total), items);
    }

    @Transactional // not readOnly: first access seeds the user's default categories
    public CategoryBreakdown categories(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        UUID userId = userId(email);
        List<Group> groups = groups(userId, from, to, categoryResolver.forUser(userId));
        Map<String, BigDecimal> cents = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        // net per (category, merchant/person) so refunds net out within their own merchant, as before
        net(groups, g -> true, g -> new CategoryKey(g.categoryName(), g.personId(), g.merchantId())).forEach((k, n) -> {
            cents.merge(k.category(), n.cents(), BigDecimal::add);
            counts.merge(k.category(), n.count(), Long::sum);
        });
        BigDecimal total = cents.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategorySpend> items = cents.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new CategorySpend(e.getKey(), money(e.getValue()), share(e.getValue(), total), counts.get(e.getKey())))
                .toList();
        return new CategoryBreakdown(from, to, CURRENCY, money(total), items);
    }

    private record CategoryKey(String category, UUID personId, UUID merchantId) {
    }

    /**
     * Money out (DEBITs) per non-spending category and, within it, per person. Credits (e.g. a repayment received)
     * are not netted here; they stay visible in cash flow.
     */
    @Transactional // not readOnly: first access seeds the user's default categories
    public NonSpendingBreakdown nonSpending(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        UUID userId = userId(email);
        Map<String, Map<UUID, Net>> byCategory = new LinkedHashMap<>();
        for (Group g : groups(userId, from, to, categoryResolver.forUser(userId))) {
            if (g.spending() || g.credit()) {
                continue;
            }
            // a null person key holds transactions not tied to a person; they count in totals only
            byCategory.computeIfAbsent(g.categoryName(), k -> new HashMap<>())
                    .merge(g.personId(), new Net(g.cents(), g.count()), SpendingAnalyticsService::add);
        }
        Map<UUID, Person> people = personRepository.findAllById(byCategory.values().stream()
                        .flatMap(m -> m.keySet().stream()).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Person::getId, Function.identity()));
        BigDecimal total = byCategory.values().stream().flatMap(m -> m.values().stream()).map(Net::cents)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<NonSpendingCategory> items = byCategory.entrySet().stream().map(e -> {
            BigDecimal cents = e.getValue().values().stream().map(Net::cents).reduce(BigDecimal.ZERO, BigDecimal::add);
            long count = e.getValue().values().stream().mapToLong(Net::count).sum();
            List<PersonSpend> persons = e.getValue().entrySet().stream()
                    .filter(p -> p.getKey() != null && people.containsKey(p.getKey()))
                    .sorted(Comparator.<Map.Entry<UUID, Net>, BigDecimal>comparing(p -> p.getValue().cents()).reversed()
                            .thenComparing(p -> people.get(p.getKey()).getName()))
                    .map(p -> new PersonSpend(p.getKey(), people.get(p.getKey()).getName(), money(p.getValue().cents()),
                            p.getValue().count()))
                    .toList();
            return new NonSpendingCategory(e.getKey(), money(cents), share(cents, total), count, persons);
        }).sorted(Comparator.comparing(NonSpendingCategory::spend).reversed()
                .thenComparing(NonSpendingCategory::category)).toList();
        return new NonSpendingBreakdown(from, to, CURRENCY, money(total), items);
    }

    @Transactional(readOnly = true)
    public CashFlow cashFlow(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        List<MonthlyCashFlow> months = new ArrayList<>();
        monthly(userId(email), from, to).forEach((month, t) -> months.add(new MonthlyCashFlow(month.toString(),
                money(t.inflowCents()), money(t.outflowCents()), money(t.inflowCents().subtract(t.outflowCents())))));
        return new CashFlow(from, to, CURRENCY, months);
    }

    @Transactional // not readOnly: first access seeds the user's default categories
    public Trends trends(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        UUID userId = userId(email);
        Resolution resolution = categoryResolver.forUser(userId);
        List<Object[]> rows = transactionRepository.sumByMonthAndCategoryGroup(userId, start(from), endExclusive(to));
        Map<UUID, Merchant> merchants = merchants(rows.stream().map(r -> (UUID) r[4]).toList());
        Map<YearMonth, BigDecimal> spend = new HashMap<>();
        for (Object[] r : rows) {
            Category c = resolution.resolve((UUID) r[2], (UUID) r[3], (UUID) r[4], merchants.get((UUID) r[4]),
                    "CREDIT".equals(r[5]));
            if ("DEBIT".equals(r[5]) && (c == null || c.isCountsAsSpending())) {
                spend.merge(YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue()), (BigDecimal) r[6],
                        BigDecimal::add);
            }
        }
        List<MonthlyTrend> months = new ArrayList<>();
        BigDecimal previous = null;
        for (YearMonth m : monthly(userId, from, to).keySet()) {
            BigDecimal outflow = spend.getOrDefault(m, BigDecimal.ZERO);
            months.add(new MonthlyTrend(m.toString(), money(outflow),
                    previous == null ? null : changePercent(outflow, previous)));
            previous = outflow;
        }
        return new Trends(from, to, CURRENCY, months);
    }

    // ---- aggregation helpers ----

    /** Inflow and count cover everything; outflow is spending only; nonSpending = all DEBITs - spending DEBITs. */
    private Totals totals(UUID userId, LocalDate from, LocalDate to, Resolution resolution) {
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal allOut = BigDecimal.ZERO;
        long count = 0;
        for (Object[] r : transactionRepository.sumByDirection(userId, start(from), endExclusive(to))) {
            if ("CREDIT".equals(r[0])) {
                in = in.add((BigDecimal) r[1]);
            } else {
                allOut = allOut.add((BigDecimal) r[1]);
            }
            count += (Long) r[2];
        }
        BigDecimal out = groups(userId, from, to, resolution).stream()
                .filter(g -> !g.credit() && g.spending())
                .map(Group::cents).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(in, out, count, allOut.subtract(out));
    }

    /** SQL groups for the range, each resolved to its effective category. */
    private List<Group> groups(UUID userId, LocalDate from, LocalDate to, Resolution resolution) {
        List<Object[]> rows = transactionRepository.sumByCategoryGroup(userId, start(from), endExclusive(to));
        Map<UUID, Merchant> merchants = merchants(rows.stream().map(r -> (UUID) r[2]).toList());
        List<Group> groups = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            UUID merchantId = (UUID) r[2];
            groups.add(new Group(resolution.resolve((UUID) r[0], (UUID) r[1], merchantId, merchants.get(merchantId),
                            "CREDIT".equals(r[3])),
                    (UUID) r[1], merchantId, "CREDIT".equals(r[3]), (BigDecimal) r[4], (Long) r[5]));
        }
        return groups;
    }

    /**
     * Net spend (DEBITs minus CREDITs) per key over spending groups, positive keys only. Credits without a merchant
     * are skipped (income, not a refund).
     */
    private static <K> Map<K, Net> net(List<Group> groups, Predicate<Group> filter, Function<Group, K> key) {
        Map<K, Net> nets = new HashMap<>();
        for (Group g : groups) {
            if (!g.spending() || !filter.test(g) || (g.credit() && g.merchantId() == null)) {
                continue;
            }
            nets.merge(key.apply(g), new Net(g.credit() ? g.cents().negate() : g.cents(), g.count()),
                    SpendingAnalyticsService::add);
        }
        nets.values().removeIf(n -> n.cents().signum() <= 0);
        return nets;
    }

    private static Net add(Net a, Net b) {
        return new Net(a.cents().add(b.cents()), a.count() + b.count());
    }

    private Map<UUID, Merchant> merchants(Collection<UUID> merchantIds) {
        List<UUID> ids = merchantIds.stream().filter(Objects::nonNull).distinct().toList();
        return merchantRepository.findAllById(ids).stream().collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    /** One entry per calendar month touching [from, to], in order, zero-filled. */
    private Map<YearMonth, Totals> monthly(UUID userId, LocalDate from, LocalDate to) {
        Map<YearMonth, Totals> months = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            months.put(m, new Totals(BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO));
        }
        for (Object[] r : transactionRepository.sumByMonthAndDirection(userId, start(from), endExclusive(to))) {
            YearMonth m = YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            BigDecimal amount = (BigDecimal) r[3];
            Totals t = months.get(m);
            months.put(m, "CREDIT".equals(r[2])
                    ? new Totals(t.inflowCents().add(amount), t.outflowCents(), t.count(), t.nonSpendingCents())
                    : new Totals(t.inflowCents(), t.outflowCents().add(amount), t.count(), t.nonSpendingCents()));
        }
        return months;
    }

    // ---- calculation rules (LLD 16) ----

    private static BigDecimal money(BigDecimal cents) {
        return cents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    /** ((current - previous) / previous) * 100; null when previous is zero. */
    static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? null : part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static Instant start(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant endExclusive(LocalDate day) {
        return start(day.plusDays(1));
    }

    private static void validate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        if (from.plusYears(MAX_RANGE_YEARS).isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Range must not exceed " + MAX_RANGE_YEARS + " years");
        }
    }

    private UUID userId(String email) {
        return userRepository.findByEmail(email).map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
