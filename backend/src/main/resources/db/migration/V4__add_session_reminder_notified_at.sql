-- M3-C: 세션 리마인더 발송 세션당 1회 멱등 기록처.
-- replay_notified_at(V1, "리플레이 열림" 알림)과 대칭 — 예정 시각 전 리마인더가 재기동·재폴링에도 1회만.
ALTER TABLE race_session
    ADD COLUMN reminder_notified_at TIMESTAMP(6) NULL AFTER replay_notified_at;
