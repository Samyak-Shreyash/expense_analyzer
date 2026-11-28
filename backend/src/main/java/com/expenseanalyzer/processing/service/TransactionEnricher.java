package com.expenseanalyzer.processing.service;

import com.expenseanalyzer.category.service.EffectiveCategoryResolver;
import com.expenseanalyzer.category.service.KeywordCategory;
import com.expenseanalyzer.merchant.service.MerchantMatch;
import com.expenseanalyzer.merchant.service.MerchantNormalizer;
import com.expenseanalyzer.merchant.service.MerchantService;
import com.expenseanalyzer.merchant.service.PayeeKey;
import com.expenseanalyzer.merchant.service.PersonNameParser;
import com.expenseanalyzer.merchant.service.PersonService;
import com.expenseanalyzer.processing.parser.pdf.GPayStatementParser;
import com.expenseanalyzer.transaction.model.Transaction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Labels transactions from their raw description: merchant or person, then keyword category. Used on import and rematch. */
@Service
public class TransactionEnricher {

    private final MerchantNormalizer merchantNormalizer;
    private final MerchantService merchantService;
    private final PersonService personService;
    private final EffectiveCategoryResolver categoryResolver;

    public TransactionEnricher(MerchantNormalizer merchantNormalizer, MerchantService merchantService,
                               PersonService personService, EffectiveCategoryResolver categoryResolver) {
        this.merchantNormalizer = merchantNormalizer;
        this.merchantService = merchantService;
        this.personService = personService;
        this.categoryResolver = categoryResolver;
    }

    /**
     * The user's own merchant name for the payee, else merchant normalisation (LLD 11) or, for a UPI P2A/P2P transfer,
     * the person paid (such a row gets no merchant); the raw description is never modified.
     */
    public void enrich(Collection<Transaction> rows, UUID userId) {
        Map<String, UUID> merchantIds = new HashMap<>();
        Map<String, UUID> personIds = new HashMap<>();
        Map<String, UUID> aliases = merchantService.aliases(userId);
        for (Transaction t : rows) {
            enrich(t, userId, merchantIds, personIds, aliases);
        }
    }

    private void enrich(Transaction t, UUID userId, Map<String, UUID> merchantIds, Map<String, UUID> personIds,
                        Map<String, UUID> aliases) {
        MerchantMatch m = merchantNormalizer.normalize(t.getRawDescription());
        t.setNormalizedDescription(m.normalizedDescription());
        String payee = PayeeKey.of(t.getRawDescription(), m.normalizedDescription());
        if (payee != null && aliases.containsKey(payee)) { // the user named this payee: wins over person and rules
            t.setMerchantId(aliases.get(payee));
            return;
        }
        Optional<String> person = PersonNameParser.parse(t.getRawDescription());
        if (person.isPresent()) {
            t.setPersonId(personIds.computeIfAbsent(person.get().toLowerCase(Locale.ROOT),
                    k -> personService.findOrCreate(userId, person.get()).getId()));
            return;
        }
        if (m.canonicalName() != null) {
            t.setMerchantId(merchantIds.computeIfAbsent(m.canonicalName(), n -> merchantService.resolve(m).getId()));
            t.setMerchantConfidence(m.confidence());
        }
    }

    /**
     * Names the person of a bank UPI transfer (P2A/P2P) by the payee in the UPI app's row for the same payment, which
     * carries the real name where the bank may give a placeholder or a shortened one.
     */
    public void namePersonFromApp(Transaction bank, String appDescription) {
        if (!PersonNameParser.isTransfer(bank.getRawDescription())) {
            return;
        }
        GPayStatementParser.payee(appDescription).ifPresent(name -> {
            bank.setMerchantId(null);
            bank.setMerchantConfidence(null);
            bank.setPersonId(personService.findOrCreate(bank.getUserId(), name).getId());
        });
    }

    /**
     * Debit rows with no effective category (own, person or merchant) take the category their description's keyword
     * names, if the user has it.
     */
    public void applyKeywordCategories(UUID userId, Collection<Transaction> rows) {
        EffectiveCategoryResolver.Resolution resolution = categoryResolver.forUser(userId);
        for (Transaction t : rows) {
            if (!"DEBIT".equals(t.getType()) || t.getMerchantId() != null || resolution.resolve(t, null) != null) {
                continue;
            }
            KeywordCategory.of(t.getRawDescription()).map(resolution::named)
                    .ifPresent(c -> t.setCategoryId(c.getId()));
        }
    }
}
