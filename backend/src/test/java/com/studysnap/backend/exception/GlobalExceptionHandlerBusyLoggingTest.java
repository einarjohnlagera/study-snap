package com.studysnap.backend.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerBusyLoggingTest {

    @Test
    void aBusyRejectionDoesNotWriteAWarnLinePerRequestButOtherAppExceptionsStillDo() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            GlobalExceptionHandler handler = new GlobalExceptionHandler(DataSize.ofMegabytes(10));
            handler.handleAppException(new PublicLibraryRankingBusyException(), new MockHttpServletRequest());
            assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);

            handler.handleAppException(new NoteNotFoundException(), new MockHttpServletRequest());
            assertThat(appender.list).anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("app_exception"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
