package com.studysnap.backend.dto;

import com.studysnap.backend.entity.SubscriptionCancellationReason;
import jakarta.validation.constraints.Size;

public record CancelPremiumSubscriptionRequest(
        SubscriptionCancellationReason reason,
        @Size(max = 1000, message = "Cancellation feedback must be 1000 characters or less.")
        String feedback
) {
}
