package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.QuizItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Manifest-count and production-dedup structural mutants for preregistration section 8. */
class ClassAStructuralDeliveryTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private QuizItem question(String stem) throws Exception {
        return JSON.readValue("{\"question\":\"" + stem
                + "\",\"choices\":[\"correct\",\"wrong B\",\"wrong C\",\"wrong D\"],\"correctIndex\":0,"
                + "\"explanation\":\"because\",\"questionFormat\":\"MCQ\"}", QuizItem.class);
    }

    @Test void shortDeliveryUsesManifestRequestedCountEvenWhenBatchHasBuffer() throws Exception {
        List<QuizItem> delivered = List.of(question("one"), question("two"));
        assertThat(AbstractClassAAdapter.structuralFailure(delivered, 3)).isEqualTo("SHORT_DELIVERY");
        assertThat(AbstractClassAAdapter.structuralFailure(delivered, 2)).isNull();
    }

    @Test void normalizedDuplicateIsARecordedStructuralEvent() throws Exception {
        List<QuizItem> delivered = List.of(question("What is a cell?"), question(" what is a cell ? "));
        assertThat(AbstractClassAAdapter.structuralFailure(delivered, 2)).isEqualTo("DUPLICATE_DELIVERY");
    }
}
