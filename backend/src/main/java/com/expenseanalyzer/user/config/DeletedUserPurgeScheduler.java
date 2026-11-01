package com.expenseanalyzer.user.config;

import com.expenseanalyzer.user.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Removes soft-deleted users. Disabled in tests, which call the service directly. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.account-purge.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DeletedUserPurgeScheduler {

    private final AccountDeletionService accountDeletionService;

    @Scheduled(fixedDelayString = "${app.account-purge.interval-ms:3600000}")
    public void purge() {
        accountDeletionService.purgeExpired();
    }
}
