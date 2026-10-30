package com.studysnap.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Injectable log boundary so failures can be proved harmless to HTTP responses. */
@Component
public class PublicApiDiagnosticsLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicApiDiagnosticsLog.class);

    public void aggregate(String line) {
        LOGGER.info("{}", line);
    }

    public void sample(String line) {
        LOGGER.info("{}", line);
    }

    public void startup(String line) {
        LOGGER.info("{}", line);
    }
}
