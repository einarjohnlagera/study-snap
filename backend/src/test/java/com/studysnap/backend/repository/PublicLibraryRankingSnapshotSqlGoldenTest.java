package com.studysnap.backend.repository;

import com.studysnap.backend.model.PublicLibrarySort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PublicLibraryRankingSnapshotSqlGoldenTest {
    @Autowired private PublicLibraryRepositoryImpl repository;

    @Test
    void snapshotSqlHasIndependentGoldensForAllSixSorts() throws Exception {
        var criteria = new PublicLibraryFilterCriteria(null, null, null, null,
                List.of("fixture-tag"), null, null, null, false, List.of());
        var snapshot = new PublicLibraryRankingSnapshot(1, OffsetDateTime.now(), Map.of(),
                Map.of(), Map.of(), Map.of("fixture-tag", Set.of(UUID.randomUUID())), 1, 1);
        for (PublicLibrarySort sort : List.of(PublicLibrarySort.RECOMMENDED,
                PublicLibrarySort.FEATURED, PublicLibrarySort.POPULAR, PublicLibrarySort.COPIED,
                PublicLibrarySort.VIEWS, PublicLibrarySort.MOST_COPIED)) {
            assertThat(repository.snapshotPageSql(criteria, sort, snapshot))
                    .isEqualTo(golden(sort, "page"));
            assertThat(repository.snapshotCountSql(criteria, sort, snapshot))
                    .isEqualTo(golden(sort, "count"));
        }
    }

    private String golden(PublicLibrarySort sort, String kind) throws Exception {
        String path = "/sql/public-library-ranking-snapshot/" + sort.name() + "." + kind + ".sql";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
