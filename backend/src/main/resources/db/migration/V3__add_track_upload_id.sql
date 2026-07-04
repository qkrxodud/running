-- V3__add_track_upload_id.sql — M2-A: 트랙 업로드 멱등 키 + GPS 공백 요약.
--
-- client_upload_id: track-api §1·§4 재업로드 정책(O-M2-4). 동일 키 재요청=기존 결과 200(멱등),
--   다른 내용 재업로드=409 TRACK_ALREADY_UPLOADED. participation당 트랙 1개(UQ(session_id,user_id))는
--   V1에서 이미 강제 — 여기서는 멱등 판별을 위해 최초 채택한 업로드 키를 보존한다.
-- gps_gap_count: 정제 시 식별한 GPS 유실 구간 수(결과 응답 gps_gap_count). track_record(요약)에 두어
--   상태·결과 조회가 track_payload 블롭을 로드하지 않고 응답하게 한다(TR-3 블롭 격리).
ALTER TABLE track_record
    ADD COLUMN client_upload_id VARCHAR(64) NULL AFTER user_id,
    ADD COLUMN gps_gap_count    INT NOT NULL DEFAULT 0 AFTER total_time_s;
