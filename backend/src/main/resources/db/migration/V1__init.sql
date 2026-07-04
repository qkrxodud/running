-- V1__init.sql — 러닝크루 초기 스키마 (설계문서 02_analyst_design.md §2 그대로)
--
-- 규약:
--  * 시각 컬럼은 전부 TIMESTAMP(6), 저장·비교·판정 전부 UTC (JVM/MySQL 세션 타임존 UTC 고정).
--  * enum은 전부 VARCHAR (@Enumerated(STRING) 대응). CHECK 제약 대신 애플리케이션 검증(코드 불변식).
--  * PK는 BIGINT AUTO_INCREMENT. 예외: invite_code(code), app_min_version(platform), track_payload(FK=PK).
--  * 문자셋 utf8mb4, 엔진 InnoDB.
--  * FK ON DELETE는 기본 RESTRICT(도메인 삭제 순서는 애플리케이션 제어 — User 탈퇴 불변식).
--    예외: device_token, track_payload = CASCADE. rank_entry = RESTRICT 명시(익명 보존).

-- 2.1 user ---------------------------------------------------------------
CREATE TABLE `user` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    nickname     VARCHAR(30)  NOT NULL,
    kakao_id     VARCHAR(64)  NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- {ACTIVE, WITHDRAWN}
    created_at   TIMESTAMP(6) NOT NULL,
    withdrawn_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_kakao_id (kakao_id),
    KEY idx_user_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.16 app_min_version ---------------------------------------------------
CREATE TABLE app_min_version (
    platform    VARCHAR(16)  NOT NULL,                     -- {ANDROID, IOS} 자연키
    min_version VARCHAR(20)  NOT NULL,                     -- semver
    updated_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (platform)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.2 device_token -------------------------------------------------------
CREATE TABLE device_token (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    fcm_token  VARCHAR(255) NOT NULL,
    platform   VARCHAR(16)  NOT NULL,                      -- {ANDROID, IOS}
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_device_token_fcm (fcm_token),
    KEY idx_device_token_user (user_id),
    CONSTRAINT fk_device_token_user FOREIGN KEY (user_id)
        REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.3 crew ---------------------------------------------------------------
CREATE TABLE crew (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50)  NOT NULL,
    leader_id  BIGINT       NOT NULL,                      -- 크루장 항상 1명(존재를 NN이 강제)
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',     -- {ACTIVE, CLOSED}
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_crew_leader (leader_id),
    KEY idx_crew_status (status),
    CONSTRAINT fk_crew_leader FOREIGN KEY (leader_id)
        REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.4 crew_member --------------------------------------------------------
CREATE TABLE crew_member (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    crew_id   BIGINT       NOT NULL,
    user_id   BIGINT       NOT NULL,
    role      VARCHAR(16)  NOT NULL,                       -- {LEADER, MEMBER}
    joined_at TIMESTAMP(6) NOT NULL,
    status    VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',      -- {ACTIVE, WITHDRAWN}
    PRIMARY KEY (id),
    UNIQUE KEY uq_crew_member (crew_id, user_id),
    KEY idx_crew_member_user (user_id),
    KEY idx_crew_member_succession (crew_id, joined_at),   -- 최선임 승계 조회
    CONSTRAINT fk_crew_member_crew FOREIGN KEY (crew_id)
        REFERENCES crew (id) ON DELETE RESTRICT,
    CONSTRAINT fk_crew_member_user FOREIGN KEY (user_id)
        REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.5 invite_code --------------------------------------------------------
CREATE TABLE invite_code (
    code       VARCHAR(16)  NOT NULL,                      -- 자연키
    crew_id    BIGINT       NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    max_uses   INT          NOT NULL,
    used_count INT          NOT NULL DEFAULT 0,            -- used_count <= max_uses (코드 불변식)
    PRIMARY KEY (code),
    KEY idx_invite_code_crew (crew_id),
    CONSTRAINT fk_invite_code_crew FOREIGN KEY (crew_id)
        REFERENCES crew (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.6 course -------------------------------------------------------------
CREATE TABLE course (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    crew_id        BIGINT       NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    route_polyline LONGTEXT     NOT NULL,                  -- 인코딩 폴리라인. 발행 후 불변(코드 불변식)
    distance_m     INT          NOT NULL,                  -- 완주 판정 기준값
    start_lat      DOUBLE       NOT NULL,
    start_lng      DOUBLE       NOT NULL,
    finish_lat     DOUBLE       NOT NULL,                  -- 도착점 반경 판정 기준
    finish_lng     DOUBLE       NOT NULL,
    created_by     BIGINT       NOT NULL,
    created_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_course_crew (crew_id),
    CONSTRAINT fk_course_crew FOREIGN KEY (crew_id)
        REFERENCES crew (id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_creator FOREIGN KEY (created_by)
        REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.7 race_session -------------------------------------------------------
CREATE TABLE race_session (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    crew_id            BIGINT       NOT NULL,
    course_id          BIGINT       NOT NULL,
    scheduled_at       TIMESTAMP(6) NOT NULL,              -- UTC
    upload_deadline    TIMESTAMP(6) NOT NULL,              -- UTC ("예정+12h"는 앱레이어 기본값)
    status             VARCHAR(16)  NOT NULL DEFAULT 'DRAFT', -- {DRAFT,OPEN,RUNNING,FINALIZING,COMPLETED,CANCELLED}
    replay_notified_at TIMESTAMP(6) NULL,                  -- FCM 세션당 1회 멱등 기록
    PRIMARY KEY (id),
    KEY idx_race_session_crew_sched (crew_id, scheduled_at),
    KEY idx_race_session_status (status),
    KEY idx_race_session_course (course_id),
    CONSTRAINT fk_race_session_crew FOREIGN KEY (crew_id)
        REFERENCES crew (id) ON DELETE RESTRICT,
    CONSTRAINT fk_race_session_course FOREIGN KEY (course_id)
        REFERENCES course (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.8 participation ------------------------------------------------------
CREATE TABLE participation (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',  -- {REGISTERED,STARTED,FINISHED,DNF,DNS,WITHDRAWN}
    PRIMARY KEY (id),
    UNIQUE KEY uq_participation (session_id, user_id),
    KEY idx_participation_user (user_id),
    CONSTRAINT fk_participation_session FOREIGN KEY (session_id)
        REFERENCES race_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_participation_user FOREIGN KEY (user_id)
        REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.9 track_record -------------------------------------------------------
CREATE TABLE track_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    session_id       BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL,
    started_at       TIMESTAMP(6) NOT NULL,                -- 각자 시작 버튼 시각
    finished_at      TIMESTAMP(6) NULL,                    -- 서버 확정(도착점 반경 최초 진입). 미확정 NULL
    total_distance_m INT          NULL,                    -- 정제 후 좌표 기반. 미정제 NULL
    total_time_s     INT          NULL,                    -- 그로스 타임. 미확정 NULL
    PRIMARY KEY (id),
    UNIQUE KEY uq_track_record (session_id, user_id),
    KEY idx_track_record_user (user_id),
    CONSTRAINT fk_track_record_session FOREIGN KEY (session_id)
        REFERENCES race_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_track_record_user FOREIGN KEY (user_id)
        REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.10 track_payload (1:1 분리, PK=FK) -----------------------------------
-- 연관(@OneToOne) 두지 않음: 순위·히스토리 조회에 블롭이 딸려오면 안 됨.
-- payload 접근은 리플레이 생성·재정제 전용 리포지토리로 한정.
CREATE TABLE track_payload (
    track_record_id BIGINT   NOT NULL,                     -- PK = FK
    raw_payload     LONGTEXT NOT NULL,                     -- 원시 트랙(JSON/JSONL). 탈퇴 시 삭제
    refined_payload LONGTEXT NULL,                         -- 정제 트랙. 재정제로 갱신
    PRIMARY KEY (track_record_id),
    CONSTRAINT fk_track_payload_record FOREIGN KEY (track_record_id)
        REFERENCES track_record (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.11 race_result -------------------------------------------------------
CREATE TABLE race_result (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    session_id   BIGINT       NOT NULL,
    finalized_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_race_result_session (session_id),         -- 세션당 1결과
    CONSTRAINT fk_race_result_session FOREIGN KEY (session_id)
        REFERENCES race_session (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.12 rank_entry --------------------------------------------------------
CREATE TABLE rank_entry (
    id             BIGINT  NOT NULL AUTO_INCREMENT,
    result_id      BIGINT  NOT NULL,
    user_id        BIGINT  NOT NULL,                        -- 탈퇴해도 행 보존(익명 표시)
    -- `rank`는 MySQL 8.0.2+ 예약어(윈도 함수) — 백틱 필수 (R-003). 컬럼명은 설계·계약대로 rank 유지.
    `rank`         INT     NULL,                            -- 완주자만. 동률 공동순위(1,1,3). DNF/DNS NULL
    record_time_s  INT     NULL,                            -- DNF/DNS NULL
    is_pb          BOOLEAN NOT NULL DEFAULT FALSE,          -- 유저×코스 기준
    PRIMARY KEY (id),
    KEY idx_rank_entry_result (result_id),
    KEY idx_rank_entry_user (user_id),
    CONSTRAINT fk_rank_entry_result FOREIGN KEY (result_id)
        REFERENCES race_result (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rank_entry_user FOREIGN KEY (user_id)
        REFERENCES `user` (id) ON DELETE RESTRICT           -- CASCADE 절대 금지(탈퇴는 익명화)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.13 replay_snapshot ---------------------------------------------------
CREATE TABLE replay_snapshot (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    session_id     BIGINT       NOT NULL,
    schema_version INT          NOT NULL,                   -- 뷰어 호환 판정
    status         VARCHAR(16)  NOT NULL,                   -- {GENERATING, READY, FAILED}
    payload        LONGTEXT     NULL,                        -- 사전계산 스냅샷(추월지점 포함). GENERATING 중 NULL
    created_at     TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_replay_snapshot_session (session_id),           -- 재생성 멱등: 복수 행 허용(최신=created_at max)
    CONSTRAINT fk_replay_snapshot_session FOREIGN KEY (session_id)
        REFERENCES race_session (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.14 reward_plan / reward_item -----------------------------------------
CREATE TABLE reward_plan (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reward_plan_session (session_id),
    CONSTRAINT fk_reward_plan_session FOREIGN KEY (session_id)
        REFERENCES race_session (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reward_item (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    plan_id     BIGINT       NOT NULL,
    -- `rank`는 MySQL 8.0.2+ 예약어 — 백틱 필수 (R-003). 컬럼명은 설계·계약대로 rank 유지.
    `rank`      INT          NOT NULL,                       -- 대상 순위
    description VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reward_item_plan (plan_id),
    CONSTRAINT fk_reward_item_plan FOREIGN KEY (plan_id)
        REFERENCES reward_plan (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 2.15 reward_grant ------------------------------------------------------
CREATE TABLE reward_grant (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    session_id BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    item_desc  VARCHAR(255) NOT NULL,                        -- 지급 시점 스냅샷(장부 보존)
    status     VARCHAR(16)  NOT NULL DEFAULT 'PENDING',      -- {PENDING, SENT, CONFIRMED}
    sent_at    TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY idx_reward_grant_session (session_id),
    KEY idx_reward_grant_user (user_id),
    CONSTRAINT fk_reward_grant_session FOREIGN KEY (session_id)
        REFERENCES race_session (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reward_grant_user FOREIGN KEY (user_id)
        REFERENCES `user` (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
