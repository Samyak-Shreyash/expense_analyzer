package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import com.expenseanalyzer.merchant.model.UserMerchantAlias;
import com.expenseanalyzer.merchant.repository.UserMerchantAliasRepository;
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

    /**
     * Find-or-create by canonical name. A concurrent create loses on the unique constraint and the
     * caller's transaction is retried, after which the merchant is found.
     */
    @Transactional
    public Merchant resolve(MerchantMatch match) {
        return merchantRepository.findByCanonicalName(match.canonicalName())
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
}
