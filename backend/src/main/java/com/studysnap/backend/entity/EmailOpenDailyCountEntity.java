package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "email_open_daily_counts")
@Getter
@Setter
@NoArgsConstructor
public class EmailOpenDailyCountEntity {
    @Id
    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "open_count", nullable = false)
    private long openCount;
}
