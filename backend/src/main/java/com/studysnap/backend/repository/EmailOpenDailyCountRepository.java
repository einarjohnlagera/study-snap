package com.studysnap.backend.repository;

import com.studysnap.backend.entity.EmailOpenDailyCountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface EmailOpenDailyCountRepository extends JpaRepository<EmailOpenDailyCountEntity, LocalDate> {
    @Modifying
    @Query(value = """
            INSERT INTO email_open_daily_counts (event_date, open_count)
            VALUES (:eventDate, 1)
            ON CONFLICT (event_date)
            DO UPDATE SET open_count = email_open_daily_counts.open_count + 1
            """, nativeQuery = true)
    void increment(@Param("eventDate") LocalDate eventDate);
}
