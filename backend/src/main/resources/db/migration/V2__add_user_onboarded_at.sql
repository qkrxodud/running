-- V2 — user.onboarded_at 추가 (설계 12_analyst_design_B.md §1.2)
-- 온보딩 완료 시각. NULL = 온보딩 미완(placeholder 닉네임 상태).
-- 서버 컬럼이 온보딩 게이트의 진실 — 클라 재설치에도 복원된다.
-- V1은 수정 금지(적용 이력 보존) — 델타는 항상 새 버전으로.
ALTER TABLE `user` ADD COLUMN onboarded_at TIMESTAMP(6) NULL;
