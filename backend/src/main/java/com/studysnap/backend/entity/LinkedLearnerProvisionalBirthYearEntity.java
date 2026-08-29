package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "linked_learner_provisional_birth_years")
@Getter
@Setter
@NoArgsConstructor
public class LinkedLearnerProvisionalBirthYearEntity {
    @Id
    @Column(name = "relationship_id")
    private UUID relationshipId;

    @Column(name = "birth_year", nullable = false)
    private Integer birthYear;

    @Column(name = "declared_at", nullable = false)
    private OffsetDateTime declaredAt;
}
