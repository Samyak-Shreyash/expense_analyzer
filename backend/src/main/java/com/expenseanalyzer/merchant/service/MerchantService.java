package com.expenseanalyzer.merchant.service;

import com.expenseanalyzer.merchant.model.Merchant;
import com.expenseanalyzer.merchant.repository.MerchantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;

    /**
     * Find-or-create by canonical name. A concurrent create loses on the unique constraint and the
     * caller's transaction is retried, after which the merchant is found.
     */
    @Transactional
    public Merchant resolve(MerchantMatch match) {
        return merchantRepository.findByCanonicalName(match.canonicalName())
                .orElseGet(() -> merchantRepository.save(new Merchant(match.canonicalName(), match.categoryHint())));
    }
}
