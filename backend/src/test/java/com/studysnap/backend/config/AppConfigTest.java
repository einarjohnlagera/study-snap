package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @Test
    void generationExecutorsKeepMainDispatchAndLlmFanOutSeparate() {
        AppConfig config = new AppConfig();
        ThreadPoolTaskExecutor studyPackExecutor = asThreadPool(config.studyPackGenerationTaskExecutor());
        ThreadPoolTaskExecutor llmParallelExecutor = asThreadPool(config.llmParallelTaskExecutor());

        try {
            assertThat(studyPackExecutor)
                    .isNotSameAs(llmParallelExecutor);
            assertThat(studyPackExecutor.getCorePoolSize()).isEqualTo(3);
            assertThat(studyPackExecutor.getMaxPoolSize()).isEqualTo(6);
            assertThat(studyPackExecutor.getQueueCapacity()).isEqualTo(100);

            assertThat(llmParallelExecutor.getCorePoolSize()).isEqualTo(4);
            assertThat(llmParallelExecutor.getMaxPoolSize()).isEqualTo(8);
            assertThat(llmParallelExecutor.getQueueCapacity()).isEqualTo(50);
        } finally {
            studyPackExecutor.shutdown();
            llmParallelExecutor.shutdown();
        }
    }

    private ThreadPoolTaskExecutor asThreadPool(AsyncTaskExecutor taskExecutor) {
        assertThat(taskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        return (ThreadPoolTaskExecutor) taskExecutor;
    }
}
