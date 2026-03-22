package com.studysnap.backend.service.jobs;

import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.UserUsageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingUsageResetJobTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserUsageService userUsageService;

    @Test
    void run_ensuresUsagePeriodForAllUsers() {
        BillingUsageResetJob job = new BillingUsageResetJob(userRepository, userUsageService);
        UUID userIdOne = UUID.randomUUID();
        UUID userIdTwo = UUID.randomUUID();
        when(userRepository.findAllUserIds()).thenReturn(List.of(userIdOne, userIdTwo));

        job.run();

        verify(userUsageService, times(2)).ensureUsagePeriod(any(UUID.class), any(OffsetDateTime.class));
    }
}
