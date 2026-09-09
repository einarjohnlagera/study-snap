CREATE INDEX idx_notifications_inbox
    ON notifications (recipient_user_id, created_at DESC)
    WHERE dismissed_at IS NULL;
