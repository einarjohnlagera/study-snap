package com.studysnap.backend.testutil.builders;

import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.UserActivityEventEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.With;

import java.time.OffsetDateTime;
import java.util.UUID;

@SuppressWarnings("unused")
@With
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserActivityEventEntityBuilder {
    private final UUID id;
    private final UUID userId;
    private final UUID studyPackId;
    private final ActivityType activityType;
    private final OffsetDateTime createdAt;

    public static UserActivityEventEntityBuilder anActivityEvent() {
        return new UserActivityEventEntityBuilder(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ActivityType.OPENED_STUDY_PACK,
                OffsetDateTime.now().minusMinutes(10)
        );
    }

    public UserActivityEventEntity build() {
        UserActivityEventEntity event = new UserActivityEventEntity();
        event.setId(id);
        event.setUserId(userId);
        event.setStudyPackId(studyPackId);
        event.setActivityType(activityType);
        event.setCreatedAt(createdAt);
        return event;
    }
}
