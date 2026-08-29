package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.security.InvitationRateLimitService;
import com.studysnap.backend.entity.LinkedLearnerRelationshipEntity;
import com.studysnap.backend.entity.LinkedLearnerSide;
import com.studysnap.backend.entity.LinkedLearnerStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.LinkedLearnerGuardianConsentRepository;
import com.studysnap.backend.repository.LinkedLearnerInvitationRepository;
import com.studysnap.backend.repository.LinkedLearnerRelationshipRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(LinkedLearnerBirthYearCorrectionTransactionTest.TestConfiguration.class)
class LinkedLearnerBirthYearCorrectionTransactionTest {
    @Autowired
    private LinkedLearnerService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LinkedLearnerRelationshipRepository relationshipRepository;

    @Autowired
    private LinkedLearnerGuardianConsentRepository consentRepository;

    private UUID learnerUserId;
    private UUID relationshipId;
    private int originalBirthYear;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists correction_relationships");
        jdbcTemplate.execute("drop table if exists correction_users");
        jdbcTemplate.execute("""
                create table correction_users (
                    id uuid primary key,
                    birth_year integer not null,
                    birth_year_updated_at timestamp with time zone,
                    updated_at timestamp with time zone not null
                )
                """);
        jdbcTemplate.execute("""
                create table correction_relationships (
                    id uuid primary key,
                    learner_user_id uuid not null,
                    status varchar(16) not null,
                    accepted_at timestamp with time zone
                )
                """);

        learnerUserId = UUID.randomUUID();
        relationshipId = UUID.randomUUID();
        originalBirthYear = Year.now().getValue() - 30;
        OffsetDateTime acceptedAt = OffsetDateTime.now().minusDays(1);
        jdbcTemplate.update(
                "insert into correction_users (id, birth_year, updated_at) values (?, ?, ?)",
                learnerUserId, originalBirthYear, Timestamp.from(acceptedAt.toInstant()));
        jdbcTemplate.update(
                "insert into correction_relationships (id, learner_user_id, status, accepted_at) values (?, ?, ?, ?)",
                relationshipId, learnerUserId, LinkedLearnerStatus.ACCEPTED.name(),
                Timestamp.from(acceptedAt.toInstant()));

        when(userRepository.findById(learnerUserId)).thenAnswer(invocation -> Optional.of(readUser()));
        // correctBirthYear now takes a PESSIMISTIC_WRITE read on the learner before deciding.
        when(userRepository.findByIdForUpdate(learnerUserId))
                .thenAnswer(invocation -> Optional.of(readUser()));
        // The correction now reads the year as a scalar under the lock and writes it with a
        // targeted update, so neither the entity read nor save() is on this path any more.
        when(userRepository.findBirthYearById(learnerUserId))
                .thenAnswer(invocation -> Optional.ofNullable(jdbcTemplate.queryForObject(
                        "select birth_year from correction_users where id = ?", Integer.class, learnerUserId)));
        when(userRepository.writeBirthYear(any(UUID.class), any(), any())).thenAnswer(invocation ->
                jdbcTemplate.update("""
                                update correction_users
                                set birth_year = ?, birth_year_updated_at = ?, updated_at = ?
                                where id = ?
                                """,
                        (Object) invocation.getArgument(1), (Object) invocation.getArgument(2),
                        (Object) invocation.getArgument(2), (Object) invocation.getArgument(0)));
        when(relationshipRepository.findByLearnerUserIdAndStatus(
                learnerUserId, LinkedLearnerStatus.ACCEPTED)).thenAnswer(invocation -> List.of(readRelationship()));
        when(consentRepository.findByRelationshipId(relationshipId)).thenReturn(Optional.empty());
        // The pause is a CONDITIONAL update now, not saveAll. Same shape of proof: write the row,
        // then fail, and assert the whole transaction unwound — including the birth year.
        when(relationshipRepository.pauseAcceptedForConsent(relationshipId)).thenAnswer(invocation -> {
            jdbcTemplate.update(
                    "update correction_relationships set status = ?, accepted_at = null where id = ?",
                    LinkedLearnerStatus.PENDING.name(), relationshipId);
            throw new IllegalStateException("forced failure after relationship write");
        });
    }

    @Test
    void failureAfterRelationshipWriteRollsBackBirthYearAndRelationship() {
        int correctedBirthYear = Year.now().getValue() - 10;

        assertThatThrownBy(() -> service.correctBirthYear(learnerUserId, correctedBirthYear))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select birth_year from correction_users where id = ?", Integer.class, learnerUserId))
                .isEqualTo(originalBirthYear);
        assertThat(jdbcTemplate.queryForObject(
                "select birth_year_updated_at from correction_users where id = ?", OffsetDateTime.class,
                learnerUserId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select status from correction_relationships where id = ?", String.class, relationshipId))
                .isEqualTo(LinkedLearnerStatus.ACCEPTED.name());
        assertThat(jdbcTemplate.queryForObject(
                "select accepted_at from correction_relationships where id = ?", OffsetDateTime.class,
                relationshipId)).isNotNull();
    }

    private UserEntity readUser() {
        return jdbcTemplate.queryForObject(
                "select id, birth_year, birth_year_updated_at, updated_at from correction_users where id = ?",
                (resultSet, rowNumber) -> {
                    UserEntity user = new UserEntity();
                    user.setId(resultSet.getObject("id", UUID.class));
                    user.setBirthYear(resultSet.getInt("birth_year"));
                    user.setBirthYearUpdatedAt(resultSet.getObject("birth_year_updated_at", OffsetDateTime.class));
                    user.setUpdatedAt(resultSet.getObject("updated_at", OffsetDateTime.class));
                    return user;
                },
                learnerUserId);
    }

    private LinkedLearnerRelationshipEntity readRelationship() {
        return jdbcTemplate.queryForObject(
                "select id, learner_user_id, status, accepted_at from correction_relationships where id = ?",
                (resultSet, rowNumber) -> {
                    LinkedLearnerRelationshipEntity relationship = new LinkedLearnerRelationshipEntity();
                    relationship.setId(resultSet.getObject("id", UUID.class));
                    relationship.setLearnerUserId(resultSet.getObject("learner_user_id", UUID.class));
                    relationship.setSupporterUserId(UUID.randomUUID());
                    relationship.setStatus(LinkedLearnerStatus.valueOf(resultSet.getString("status")));
                    relationship.setInitiatedBy(LinkedLearnerSide.SUPPORTER);
                    relationship.setAcceptedAt(resultSet.getObject("accepted_at", OffsetDateTime.class));
                    return relationship;
                },
                relationshipId);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        LinkedLearnerRelationshipRepository relationshipRepository() {
            return mock(LinkedLearnerRelationshipRepository.class);
        }

        @Bean
        LinkedLearnerGuardianConsentRepository consentRepository() {
            return mock(LinkedLearnerGuardianConsentRepository.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        LinkedLearnerService linkedLearnerService(
                LinkedLearnerRelationshipRepository relationshipRepository,
                LinkedLearnerGuardianConsentRepository consentRepository,
                UserRepository userRepository
        ) {
            return new LinkedLearnerService(
                    relationshipRepository,
                    mock(LinkedLearnerInvitationRepository.class),
                    consentRepository,
                    mock(com.studysnap.backend.repository.LinkedLearnerGrantRepository.class),
                    mock(com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository.class),
                    userRepository,
                    mock(OnboardingGuardService.class),
                    mock(AuthService.class),
                    mock(EmailService.class),
                    mock(EmailTemplateService.class),
                    new StudySnapProperties(),
                    new GuardianConsentPolicy(new StudySnapProperties()),
                    mock(InvitationRateLimitService.class));
        }
    }
}
