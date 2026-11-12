package com.expenseanalyzer.audit.config;

import com.expenseanalyzer.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Removes audit rows past retention. Disabled in tests, which call the service directly. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.audit-purge.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogPurgeScheduler {

    private final AuditService auditService;

    @Scheduled(fixedDelayString = "${app.audit-purge.interval-ms:3600000}")
    public void purge() {
        auditService.purgeExpired();
    }
}
