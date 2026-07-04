package com.studysnap.backend.service;

import com.studysnap.backend.dto.MemorizationCardResponse;
import com.studysnap.backend.entity.MemorizationCardEntity;
import com.studysnap.backend.entity.MemorizationGrade;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.MemorizationNotAvailableException;
import com.studysnap.backend.repository.MemorizationCardRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemorizationCardServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-04T08:00:00Z");
    private static final String RAW_CONCEPT = "  Cell Cycle  ";
    private static final String NORMALIZED_CONCEPT = "cell cycle";

    @Mock
    private MemorizationCardRepository memorizationCardRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private UserRepository userRepository;

    private MemorizationCardService memorizationCardService;
    private UUID userId;
    private UUID studyPackId;

    @BeforeEach
    void setUp() {
        memorizationCardService = new MemorizationCardService(
            memorizationCardRepository,
            studyPackRepository,
            userRepository
        );
        userId = UUID.randomUUID();
        studyPackId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setProfileType(ProfileType.STUDENT);
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(studyPackId);
        studyPack.setOwnerUserId(userId);
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)).thenReturn(Optional.of(studyPack));
        lenient().when(memorizationCardRepository.save(org.mockito.ArgumentMatchers.any(MemorizationCardEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordGrade_createsNewCardAndSchedulesGoodFirstSuccessForTomorrow() {
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.empty());

        MemorizationCardResponse response = memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.GOOD,
            NOW
        );

        ArgumentCaptor<MemorizationCardEntity> captor = ArgumentCaptor.forClass(MemorizationCardEntity.class);
        verify(memorizationCardRepository).save(captor.capture());
        MemorizationCardEntity saved = captor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConcept()).isEqualTo(NORMALIZED_CONCEPT);
        assertThat(saved.getRepetitions()).isEqualTo(1);
        assertThat(saved.getIntervalDays()).isEqualTo(1);
        assertThat(saved.getEaseFactor()).isEqualByComparingTo("2.50");
        assertThat(saved.getDueAt()).isEqualTo(NOW.plusDays(1));
        assertThat(saved.getLastReviewedAt()).isEqualTo(NOW);
        assertThat(saved.getLastGrade()).isEqualTo(MemorizationGrade.GOOD);
        assertThat(response.concept()).isEqualTo(NORMALIZED_CONCEPT);
    }

    @Test
    void recordGrade_againResetsRepetitionsAndLowersEaseFactorToNow() {
        MemorizationCardEntity existing = existingCard(3, 6, "1.40");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.AGAIN,
            NOW
        );

        assertThat(existing.getRepetitions()).isZero();
        assertThat(existing.getIntervalDays()).isZero();
        assertThat(existing.getEaseFactor()).isEqualByComparingTo("1.30");
        assertThat(existing.getDueAt()).isEqualTo(NOW);
    }

    @Test
    void recordGrade_againDoesNotLowerEaseFactorBelowFloor() {
        MemorizationCardEntity existing = existingCard(2, 4, "1.30");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.AGAIN,
            NOW
        );

        assertThat(existing.getEaseFactor()).isEqualByComparingTo("1.30");
    }

    @Test
    void recordGrade_hardIncrementsRepetitionsAndUsesShortInterval() {
        MemorizationCardEntity existing = existingCard(1, 5, "2.50");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.HARD,
            NOW
        );

        assertThat(existing.getRepetitions()).isEqualTo(2);
        assertThat(existing.getIntervalDays()).isEqualTo(6);
        assertThat(existing.getEaseFactor()).isEqualByComparingTo("2.35");
        assertThat(existing.getDueAt()).isEqualTo(NOW.plusDays(6));
    }

    @Test
    void recordGrade_hardUsesAtLeastOneDayForZeroInterval() {
        MemorizationCardEntity existing = existingCard(0, 0, "2.50");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.HARD,
            NOW
        );

        assertThat(existing.getIntervalDays()).isEqualTo(1);
        assertThat(existing.getDueAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void recordGrade_goodSubsequentSuccessMultipliesPreviousIntervalByEase() {
        MemorizationCardEntity existing = existingCard(2, 4, "2.50");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.GOOD,
            NOW
        );

        assertThat(existing.getRepetitions()).isEqualTo(3);
        assertThat(existing.getIntervalDays()).isEqualTo(10);
        assertThat(existing.getEaseFactor()).isEqualByComparingTo("2.50");
        assertThat(existing.getDueAt()).isEqualTo(NOW.plusDays(10));
    }

    @Test
    void recordGrade_easyFirstSuccessSchedulesFourDaysAndRaisesEaseFactor() {
        MemorizationCardEntity existing = existingCard(0, 0, "2.50");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.EASY,
            NOW
        );

        assertThat(existing.getRepetitions()).isEqualTo(1);
        assertThat(existing.getIntervalDays()).isEqualTo(4);
        assertThat(existing.getEaseFactor()).isEqualByComparingTo("2.65");
        assertThat(existing.getDueAt()).isEqualTo(NOW.plusDays(4));
    }

    @Test
    void recordGrade_easySubsequentSuccessMultipliesIntervalWithBonusAndRaisesEaseFactor() {
        MemorizationCardEntity existing = existingCard(2, 4, "2.50");
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.EASY,
            NOW
        );

        assertThat(existing.getRepetitions()).isEqualTo(3);
        assertThat(existing.getIntervalDays()).isEqualTo(13);
        assertThat(existing.getEaseFactor()).isEqualByComparingTo("2.65");
        assertThat(existing.getDueAt()).isEqualTo(NOW.plusDays(13));
    }

    @Test
    void recordGrade_updatesExistingRowInPlace() {
        MemorizationCardEntity existing = existingCard(1, 2, "2.50");
        UUID existingId = existing.getId();
        when(memorizationCardRepository.findByUserIdAndStudyPackIdAndConcept(userId, studyPackId, NORMALIZED_CONCEPT))
            .thenReturn(Optional.of(existing));

        memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.GOOD,
            NOW
        );

        ArgumentCaptor<MemorizationCardEntity> captor = ArgumentCaptor.forClass(MemorizationCardEntity.class);
        verify(memorizationCardRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existingId);
    }

    @Test
    void recordGrade_blocksTeacherProfilesBeforeWritingSchedule() {
        UserEntity teacher = new UserEntity();
        teacher.setId(userId);
        teacher.setProfileType(ProfileType.TEACHER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> memorizationCardService.recordGradeForOwnedStudyPack(
            userId,
            studyPackId.toString(),
            RAW_CONCEPT,
            MemorizationGrade.GOOD,
            NOW
        )).isInstanceOf(MemorizationNotAvailableException.class);

        verify(memorizationCardRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private MemorizationCardEntity existingCard(int repetitions, int intervalDays, String easeFactor) {
        MemorizationCardEntity entity = new MemorizationCardEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setStudyPackId(studyPackId);
        entity.setConcept(NORMALIZED_CONCEPT);
        entity.setRepetitions(repetitions);
        entity.setIntervalDays(intervalDays);
        entity.setEaseFactor(new BigDecimal(easeFactor));
        entity.setDueAt(NOW.minusDays(1));
        entity.setCreatedAt(NOW.minusDays(10));
        entity.setUpdatedAt(NOW.minusDays(1));
        return entity;
    }
}
