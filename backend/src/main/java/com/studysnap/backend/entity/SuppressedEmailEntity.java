package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "suppressed_email")
@Getter
@Setter
@NoArgsConstructor
public class SuppressedEmailEntity {
    @Id
    @Column(name = "address", nullable = false, length = 320)
    private String address;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
