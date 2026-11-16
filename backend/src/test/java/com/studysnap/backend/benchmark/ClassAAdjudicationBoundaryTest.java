package com.studysnap.backend.benchmark;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ClassAAdjudicationBoundaryTest {
    private static final List<Class<?>> ENGINE = List.of(InvalidEvidence.class,InvalidExpansion.class,
            CountRange.class,QuestionStatus.class,FinalStatus.class,ReviewSets.class,
            Expansion.class,Expansion.WorkflowScope.class,Expansion.ExpansionPlan.class,
            Expansion.PairReview.class,Expansion.ExpansionResult.class,Expansion.Feasibility.class,
            PairEvidence.class,PairEvidence.OutputEvidence.class,LockReadiness.class);

    @Test void engineSignaturesAcceptNoKeyPathOrLabel() throws Exception {
        for (Class<?> type : ENGINE) {
            for (Method method : type.getDeclaredMethods()) for (var parameter : method.getParameters()) {
                assertThat(parameter.getType()).as("%s.%s",type.getSimpleName(),method.getName())
                        .isNotEqualTo(Path.class).isNotEqualTo(File.class);
                assertThat(parameter.getType().getSimpleName().toLowerCase()).doesNotContain("openedkey","armkey");
                if (parameter.isNamePresent()) assertThat(parameter.getName().toLowerCase())
                        .doesNotStartWith("arm").doesNotContain("key");
            }
            if (type.getRecordComponents() != null) for (var component : type.getRecordComponents())
                assertThat(component.getName().toLowerCase()).doesNotStartWith("arm").doesNotContain("key");
        }
    }

    @Test void engineSourcesContainNoKeyAccessReference() throws Exception {
        Path root = Path.of("src/test/java/com/studysnap/backend/benchmark");
        for (Class<?> type : ENGINE) {
            if (type.getEnclosingClass() != null) continue;
            String source = Files.readString(root.resolve(type.getSimpleName()+".java"));
            assertThat(source).doesNotContain("Arm"+"KeyAccess","Opened"+"Key");
        }
        assertThat(Arrays.stream(Expansion.ExpansionResult.class.getRecordComponents())
                .map(c -> c.getName()).toList()).containsExactly("outcome","unresolvedFindingPresent","reason");
    }
}
