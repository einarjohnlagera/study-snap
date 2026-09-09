package com.studysnap.backend.service;

import com.studysnap.backend.dto.CreatorImpactPageResponse;
import com.studysnap.backend.dto.CreatorImpactSummaryResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.CreatorImpactNoteProjection;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreatorImpactServiceTest {
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    private CreatorImpactService creatorImpactService;

    @BeforeEach
    void setUp() {
        creatorImpactService = new CreatorImpactService(noteRepository, analyticsEventRepository);
    }

    @Test
    void getMineRunsGroupedQueriesOverTheRequestedPageInsteadOfTheCreatorsWholeCatalog() {
        UUID creatorUserId = UUID.randomUUID();
        List<CreatorImpactNoteProjection> page = List.of(
                impactProjection(UUID.randomUUID(), "Cell Biology", 4),
                impactProjection(UUID.randomUUID(), "Human Anatomy", 2),
                impactProjection(UUID.randomUUID(), "Pharmacology", 1)
        );
        List<UUID> pageIds = page.stream().map(CreatorImpactNoteProjection::getNoteId).toList();
        when(noteRepository.findImpactedCreatorNotes(creatorUserId, PageRequest.of(2, 3))).thenReturn(page);
        when(noteRepository.countImpactedNotesByCreatorUserId(creatorUserId)).thenReturn(61L);
        when(noteRepository.countByOwnerUserIdAndVisibility(creatorUserId, NoteVisibility.PUBLIC)).thenReturn(1_587L);
        when(noteRepository.countCopiedPublicNotesBySourceNoteIds(pageIds)).thenReturn(List.of(
                copyProjection(pageIds.getFirst(), 8)
        ));
        when(analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                AnalyticsEventType.PUBLIC_NOTE_VIEWED,
                pageIds
        )).thenReturn(List.of(eventProjection(pageIds.getFirst(), 21)));

        CreatorImpactPageResponse response = creatorImpactService.getMine(creatorUserId, true, 2, 3);

        assertThat(response.notes()).hasSize(3);
        assertThat(response.totalImpacted()).isEqualTo(61);
        assertThat(response.totalZeroImpact()).isEqualTo(1_526);
        ArgumentCaptor<List<UUID>> copyIds = ArgumentCaptor.forClass(List.class);
        verify(noteRepository).countCopiedPublicNotesBySourceNoteIds(copyIds.capture());
        assertThat(copyIds.getValue()).containsExactlyElementsOf(pageIds).hasSize(3);
        ArgumentCaptor<List<UUID>> viewIds = ArgumentCaptor.forClass(List.class);
        verify(analyticsEventRepository).countPublicNoteEventsByTypeAndNoteIds(
                org.mockito.ArgumentMatchers.eq(AnalyticsEventType.PUBLIC_NOTE_VIEWED),
                viewIds.capture()
        );
        assertThat(viewIds.getValue()).containsExactlyElementsOf(pageIds).hasSize(3);
    }

    @Test
    void getMineCapsTheRequestedPageSizeOnTheServer() {
        UUID creatorUserId = UUID.randomUUID();

        creatorImpactService.getMine(creatorUserId, false, 0, 10_000);

        verify(noteRepository).findZeroImpactCreatorNotes(
                creatorUserId,
                PageRequest.of(0, CreatorImpactService.MAX_PAGE_SIZE)
        );
    }

    @Test
    void getMineClampsDegeneratePagingInputsInsteadOfThrowing() {
        UUID creatorUserId = UUID.randomUUID();

        creatorImpactService.getMine(creatorUserId, true, -3, 0);

        verify(noteRepository).findImpactedCreatorNotes(creatorUserId, PageRequest.of(0, 1));
    }

    @Test
    void anEmptyPageIsValidAndSkipsBothGroupedQueries() {
        UUID creatorUserId = UUID.randomUUID();
        when(noteRepository.findImpactedCreatorNotes(
                creatorUserId,
                PageRequest.of(99, CreatorImpactService.DEFAULT_PAGE_SIZE)
        )).thenReturn(List.of());

        CreatorImpactPageResponse response = creatorImpactService.getMine(
                creatorUserId,
                true,
                99,
                CreatorImpactService.DEFAULT_PAGE_SIZE
        );

        assertThat(response.notes()).isEmpty();
        assertThat(response.page()).isEqualTo(99);
        verify(noteRepository, never()).countCopiedPublicNotesBySourceNoteIds(anyList());
        verify(analyticsEventRepository, never()).countPublicNoteEventsByTypeAndNoteIds(
                org.mockito.ArgumentMatchers.any(),
                anyList()
        );
    }

    @Test
    void summaryUsesTheCreatorWideDeduplicatedHeadlineAndPublicNoteCount() {
        UUID creatorUserId = UUID.randomUUID();
        when(noteRepository.countDistinctLearnersHelpedByCreatorUserId(creatorUserId)).thenReturn(56L);
        when(noteRepository.countByOwnerUserIdAndVisibility(creatorUserId, NoteVisibility.PUBLIC))
                .thenReturn(1_587L);

        CreatorImpactSummaryResponse response = creatorImpactService.getSummary(creatorUserId);

        assertThat(response.distinctLearnersHelped()).isEqualTo(56);
        assertThat(response.publicNoteCount()).isEqualTo(1_587);
    }

    private CreatorImpactNoteProjection impactProjection(UUID noteId, String title, long learnerCount) {
        return new CreatorImpactNoteProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public long getLearnerCount() {
                return learnerCount;
            }
        };
    }

    private NoteCopyCountProjection copyProjection(UUID noteId, long copyCount) {
        return new NoteCopyCountProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getCopyCount() {
                return copyCount;
            }
        };
    }

    private PublicNoteEventCountProjection eventProjection(UUID noteId, long totalCount) {
        return new PublicNoteEventCountProjection() {
            @Override
            public UUID getNoteId() {
                return noteId;
            }

            @Override
            public long getTotalCount() {
                return totalCount;
            }
        };
    }
}
