package com.expenseanalyzer.analytics.service;

import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CashFlow;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CategoryBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.CategorySpend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MerchantBreakdown;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MerchantSpend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MonthlyCashFlow;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.MonthlyTrend;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.PeriodTotals;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.SpendingSummary;
import com.expenseanalyzer.analytics.dto.AnalyticsDtos.Trends;
import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deterministic spending analytics (LLD sections 15-16), aggregated in the database.
 * <p>
 * Semantics: dates are inclusive UTC days. Inflow = all CREDITs (including refunds), outflow = all DEBITs.
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

    private record Totals(BigDecimal inflowCents, BigDecimal outflowCents, long count) {
    }

    /** Per-merchant net spend in cents, before names are attached. */
    private record MerchantCents(UUID merchantId, BigDecimal spendCents, long count) {
    }

    @Transactional(readOnly = true)
    public SpendingSummary summary(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        UUID userId = userId(email);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);

        Totals cur = totals(userId, from, to);
        Totals prev = totals(userId, prevFrom, prevTo);
        PeriodTotals previous = new PeriodTotals(prevFrom, prevTo, money(prev.inflowCents()), money(prev.outflowCents()),
                money(prev.inflowCents().subtract(prev.outflowCents())), prev.count());
        return new SpendingSummary(from, to, CURRENCY, money(cur.inflowCents()), money(cur.outflowCents()),
                money(cur.inflowCents().subtract(cur.outflowCents())), cur.count(), previous,
                changePercent(cur.inflowCents(), prev.inflowCents()),
                changePercent(cur.outflowCents(), prev.outflowCents()));
    }

    @Transactional(readOnly = true)
    public MerchantBreakdown merchants(String email, LocalDate from, LocalDate to, int limit) {
        validate(from, to);
        List<MerchantCents> spend = merchantSpend(userId(email), from, to);
        BigDecimal total = spend.stream().map(MerchantCents::spendCents).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, Merchant> merchants = merchants(spend);
        List<MerchantSpend> items = spend.stream()
                .limit(limit)
                .map(m -> new MerchantSpend(m.merchantId(),
                        m.merchantId() == null ? UNMATCHED : merchants.get(m.merchantId()).getCanonicalName(),
                        money(m.spendCents()), share(m.spendCents(), total), m.count()))
                .toList();
        return new MerchantBreakdown(from, to, CURRENCY, money(total), items);
    }

    @Transactional(readOnly = true)
    public CategoryBreakdown categories(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        List<MerchantCents> spend = merchantSpend(userId(email), from, to);
        Map<UUID, Merchant> merchants = merchants(spend);
        Map<String, BigDecimal> cents = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (MerchantCents m : spend) {
            String category = m.merchantId() == null ? UNCATEGORIZED
                    : Objects.requireNonNullElse(merchants.get(m.merchantId()).getCategoryHint(), UNCATEGORIZED);
            cents.merge(category, m.spendCents(), BigDecimal::add);
            counts.merge(category, m.count(), Long::sum);
        }
        BigDecimal total = cents.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategorySpend> items = cents.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new CategorySpend(e.getKey(), money(e.getValue()), share(e.getValue(), total), counts.get(e.getKey())))
                .toList();
        return new CategoryBreakdown(from, to, CURRENCY, money(total), items);
    }

    @Transactional(readOnly = true)
    public CashFlow cashFlow(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        List<MonthlyCashFlow> months = new ArrayList<>();
        monthly(userId(email), from, to).forEach((month, t) -> months.add(new MonthlyCashFlow(month.toString(),
                money(t.inflowCents()), money(t.outflowCents()), money(t.inflowCents().subtract(t.outflowCents())))));
        return new CashFlow(from, to, CURRENCY, months);
    }

    @Transactional(readOnly = true)
    public Trends trends(String email, LocalDate from, LocalDate to) {
        validate(from, to);
        List<MonthlyTrend> months = new ArrayList<>();
        BigDecimal previous = null;
        for (Map.Entry<YearMonth, Totals> e : monthly(userId(email), from, to).entrySet()) {
            BigDecimal outflow = e.getValue().outflowCents();
            months.add(new MonthlyTrend(e.getKey().toString(), money(outflow),
                    previous == null ? null : changePercent(outflow, previous)));
            previous = outflow;
        }
        return new Trends(from, to, CURRENCY, months);
    }

    // ---- aggregation helpers ----

    private Totals totals(UUID userId, LocalDate from, LocalDate to) {
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        long count = 0;
        for (Object[] r : transactionRepository.sumByDirection(userId, start(from), endExclusive(to))) {
            if ("CREDIT".equals(r[0])) {
                in = in.add((BigDecimal) r[1]);
            } else {
                out = out.add((BigDecimal) r[1]);
            }
            count += (Long) r[2];
        }
        return new Totals(in, out, count);
    }

    /** Net spend per merchant (null id = unmatched), positive groups only, largest first. */
    private List<MerchantCents> merchantSpend(UUID userId, LocalDate from, LocalDate to) {
        Map<UUID, BigDecimal> cents = new HashMap<>();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] r : transactionRepository.sumByMerchantAndDirection(userId, start(from), endExclusive(to))) {
            UUID merchantId = (UUID) r[0];
            boolean credit = "CREDIT".equals(r[1]);
            if (credit && merchantId == null) {
                continue; // unmatched inflow is income, not a refund
            }
            BigDecimal amount = (BigDecimal) r[2];
            cents.merge(merchantId, credit ? amount.negate() : amount, BigDecimal::add);
            counts.merge(merchantId, (Long) r[3], Long::sum);
        }
        return cents.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .map(e -> new MerchantCents(e.getKey(), e.getValue(), counts.get(e.getKey())))
                .sorted(Comparator.comparing(MerchantCents::spendCents).reversed()
                        .thenComparing(m -> String.valueOf(m.merchantId())))
                .toList();
    }

    private Map<UUID, Merchant> merchants(List<MerchantCents> spend) {
        List<UUID> ids = spend.stream().map(MerchantCents::merchantId).filter(Objects::nonNull).toList();
        return merchantRepository.findAllById(ids).stream().collect(Collectors.toMap(Merchant::getId, Function.identity()));
    }

    /** One entry per calendar month touching [from, to], in order, zero-filled. */
    private Map<YearMonth, Totals> monthly(UUID userId, LocalDate from, LocalDate to) {
        Map<YearMonth, Totals> months = new LinkedHashMap<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            months.put(m, new Totals(BigDecimal.ZERO, BigDecimal.ZERO, 0));
        }
        for (Object[] r : transactionRepository.sumByMonthAndDirection(userId, start(from), endExclusive(to))) {
            YearMonth m = YearMonth.of(((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            BigDecimal amount = (BigDecimal) r[3];
            Totals t = months.get(m);
            months.put(m, "CREDIT".equals(r[2])
                    ? new Totals(t.inflowCents().add(amount), t.outflowCents(), t.count())
                    : new Totals(t.inflowCents(), t.outflowCents().add(amount), t.count()));
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
