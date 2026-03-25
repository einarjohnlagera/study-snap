package com.studysnap.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudySnapPropertiesTest {

    @Test
    void limitMegabyteValuesConvertToBytes() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getLimits().setFileUploadMaxSize(10);
        properties.getLimits().setTxtUploadMaxSize(1);
        properties.getLimits().setPdfUploadMaxSize(10);
        properties.getLimits().setDocxUploadMaxSize(5);

        assertThat(properties.getLimits().getFileUploadMaxSizeBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(properties.getLimits().getTxtUploadMaxSizeBytes()).isEqualTo(1024L * 1024L);
        assertThat(properties.getLimits().getPdfUploadMaxSizeBytes()).isEqualTo(10L * 1024L * 1024L);
        assertThat(properties.getLimits().getDocxUploadMaxSizeBytes()).isEqualTo(5L * 1024L * 1024L);
    }
}
