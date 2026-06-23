package com.studysnap.backend.service.jobs;

import com.studysnap.backend.service.AccountPurgeService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountPurgeSchedulerTest {
    @Test
    void run_invokesAccountPurgeService() {
        AccountPurgeService accountPurgeService = mock(AccountPurgeService.class);
        when(accountPurgeService.purgeEligibleAccounts(any()))
                .thenReturn(new AccountPurgeService.AccountPurgeSummary(2, 1));
        AccountPurgeScheduler scheduler = new AccountPurgeScheduler(accountPurgeService);

        scheduler.run();

        verify(accountPurgeService).purgeEligibleAccounts(any());
    }
}
