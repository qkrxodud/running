---
name: domain-model
description: 러닝크루 앱의 도메인 모델 규범 — 6개 바운디드 컨텍스트, 애그리거트, 불변식, 상태머신, 이벤트 흐름, 데이터 모델, 레이스 규칙의 요약본. 도메인 로직 구현·설계·검토·테스트 기대값 도출 등 도메인 관련 작업 전에 반드시 읽을 것. 크루, 레이스, 트래킹, 순위, 보상, 리플레이, 완주 판정, 탈퇴 처리를 다루는 모든 작업이 대상.
---

# 도메인 모델 규범

계획서(`app/docs/러닝크루_앱_계획서.md`) §4~§7의 규범 요약. 이 문서와 계획서가 충돌하면 **계획서가 우선**하며, 충돌 발견 시 이 스킬을 갱신하라.

## 컨텍스트 지도와 이벤트 흐름

6개 바운디드 컨텍스트(User, Crew, Race, Tracking, Ranking, Reward) + 독립 프로젝션 패키지 `replay`. MVP는 모놀리스 내 패키지 분리(헥사고날). **컨텍스트 간 결합은 이벤트로만 푼다** — 타 컨텍스트의 리포지토리·서비스 직접 호출 금지.

```
TrackUploaded ─▶ TrackRefinement(정제) ─▶ FinishPolicy(완주/DNF 판정)
             ─▶ RaceSession(전원 완료 체크) ─▶ RaceCompleted
RaceCompleted ─▶ RankingPolicy 산정 ─▶ ResultFinalized
ResultFinalized ─(커밋 후 비동기)▶ ReplaySnapshot GENERATING → READY ─▶ FCM(세션당 1회)
              │                                        └─▶ FAILED → 운영 재생성
              └▶ RewardGrant 생성 (PENDING)
```

## 레이스 규칙 (MVP 확정 — 임의 변경 금지)

| 규칙 | 내용 | 이유 |
|------|------|------|
| 기록 방식 | **그로스 타임** — 자동 일시정지 없음 | 단순, 분쟁 없음, 정지 감지 오작동 제거 |
| 기록 구간 | 시작 버튼 ~ **도착점 반경 최초 진입 시각 자동 확정** | 종료 버튼 늦게 눌러도 손해 없게. 종료 버튼은 트래킹 중단+업로드 트리거만 |
| 출발 | 각자 시작 버튼 기준. 동시 출발 강제 없음 | 순위는 개인 소요 시간이므로 공정 |
| 리플레이 | 각자 시작 시점 t=0 **상대 시각 동기화** 병합 | 2차 비동기 레이스가 로직 변경 없이 지원됨 |
| 완주 판정 | ①도착점 반경 30m 진입 ②정제 후 거리 ≥ 코스 거리 90% ③정제 포인트 80% 이상이 코스 폴리라인 50m 이내 — **3조건 모두** | 지름길·경로 이탈 판정(안티치트 아님). 미충족 시 DNF, 단 기록·경로는 보존해 리플레이 표시 |

임계값(30m/90%/80%/50m)은 운영 튜닝 대상 → 상수는 설정 가능하게 두되 기본값은 위 값.

## 컨텍스트별 핵심 불변식

**User**: 탈퇴 시 — 식별 정보(닉네임, kakao_id, 푸시 토큰) **즉시 파기**, track_payload(raw/refined) **삭제**, 과거 순위·rank_entry·ReplaySnapshot 내 경로는 **"탈퇴한 러너"로 익명화 보존**. 원칙: "식별 정보와 위치 원본은 파기, 식별자가 분리된 파생물은 익명 보존". 남은 크루원의 리플레이·히스토리가 깨지면 안 되기 때문.

**Crew**: 크루장 항상 1명 존재. 초대 코드로만 가입(공개 경로 없음). 크루장 탈퇴 → 가입일 최선 멤버 자동 승계. 마지막 1인 탈퇴 → 크루 CLOSED.

**Race**:
- **발행(OPEN 이후 세션에서 사용)된 코스는 불변** — 수정 필요 시 새 코스 생성. 과거 리플레이·PB의 참조가 깨지기 때문.
- RaceStatus: `DRAFT → OPEN → RUNNING → FINALIZING → COMPLETED / CANCELLED`. COMPLETED는 전원 업로드 완료 또는 upload_deadline 경과 시에만.
- Participation 상태(서버): `REGISTERED / STARTED / FINISHED / DNF / DNS / WITHDRAWN`.
- 취소: 크루장은 RUNNING 중에도 취소 가능. CANCELLED는 순위·보상 미생성, 단 이미 뛰던 트랙은 **개인 기록(세션 무관)으로 보존**.
- 클라이언트 로컬 상태머신은 별도: `READY → RUNNING → FINISHED_LOCAL → UPLOADED`. FINISHED_LOCAL은 서버가 모르는 상태 — 서버 상태에 넣지 말 것.

**Tracking**:
- 총 거리·페이스는 **정제 후 좌표로 계산** (원시 하버사인 누적은 노이즈로 부풀음).
- 타임스탬프는 기기 시계가 아닌 **GPS 시각 우선**.
- 원시+정제 **둘 다 보존** — 정제 알고리즘 개선 시 과거 기록 재계산 가능해야 함.
- 저장: `track_record`(메타·요약) : `track_payload`(raw/refined 블롭) = 1:1 분리. 조회는 record만, 블롭 접근은 리플레이 생성·재정제 전용 경로 한정.
- 샘플링: 적응형 — 주행 중 3~5초, 정지 지속 시 10초, 재개 시 복귀.

**Ranking**:
- 순위: 완주 기록 오름차순. **동률은 공동 순위 + 다음 순위 건너뜀 (1, 1, 3)**. DNF/DNS는 순위 미부여, 목록 하단 표기.
- PB는 유저×코스(course_id) 기준 — 코스 불변이라 참조가 안정적. 거리대 PB는 2차.

**Reward**: MVP는 수동 지급 장부. RewardGrant 상태 `PENDING/SENT/CONFIRMED`. 기프티콘 API·Outbox는 2차.

**Replay** (프로젝션 — 애그리거트 아님, 독립 패키지 `replay`):
- ResultFinalized **커밋 후 비동기** 생성 (MVP 규모는 AFTER_COMMIT 리스너로 충분, 잡 큐 불필요).
- ReplaySnapshot은 `schema_version` + 상태(`GENERATING → READY / FAILED`) 보유. FAILED는 관측·재시도 가능해야 함.
- 원시 트랙 보존 전제로 **삭제 후 재생성 멱등** — 재생성 운영 도구(관리 API/스크립트)는 처음부터 마련.
- FCM "리플레이 열림"은 **세션당 1회 멱등** (`race_session.replay_notified_at`으로 기록). 재생성 READY에 재발송 금지.
- 추월 지점(동일 진행거리 도달 시각 비교)은 서버에서 사전 계산해 스냅샷에 포함.

## 순수 함수 경계 (골든 테스트 대상)

FinishPolicy, TrackRefinementService, RankingPolicy, 추월 계산·스냅샷 병합은 **IO·시계·랜덤 없는 순수 함수**로 구현한다. 설계·리뷰 시 이 경계가 무너지면 반려. 이유: 실주행 픽스처 기반 골든 테스트가 제품 정확성의 회귀 방지선이기 때문.

## 데이터 모델 (핵심 테이블)

```
user(id, nickname, kakao_id, status, created_at, withdrawn_at)
device_token(id, user_id, fcm_token, platform, updated_at)
crew(id, name, leader_id, status, created_at)
crew_member(id, crew_id, user_id, role, joined_at, status)
invite_code(code, crew_id, expires_at, max_uses, used_count)
course(id, crew_id, name, route_polyline, distance_m,
       start_lat, start_lng, finish_lat, finish_lng, created_by)
race_session(id, crew_id, course_id, scheduled_at, upload_deadline,  -- NOT NULL
             status, replay_notified_at)
participation(id, session_id, user_id, status)
track_record(id, session_id, user_id, started_at, finished_at,
             total_distance_m, total_time_s)
track_payload(track_record_id, raw_payload, refined_payload)   -- 1:1
race_result(id, session_id, finalized_at)
rank_entry(id, result_id, user_id, rank, record_time_s, is_pb)
replay_snapshot(id, session_id, schema_version, status, payload, created_at)
reward_plan(id, session_id) / reward_item(plan_id, rank, description)
reward_grant(id, session_id, user_id, item_desc, status, sent_at)
app_min_version(platform, min_version, updated_at)
```

`upload_deadline`의 "예정 +12시간"은 도메인 규칙이 아니라 **애플리케이션 레이어 UX 기본값** — 도메인에 하드코딩 금지.

## API 계약 관리

앱↔서버 공유 스키마는 `docs/contracts/{이름}.md`가 진실이다. 계약 변경은 domain-analyst 경유로만, 변경 시 flutter-dev·backend-dev 양쪽 통지. 서버는 플랫폼 무지 — 계약에 플랫폼 종속 필드 금지(디버깅용 client_meta 예외).

## 범위 가드

범위 외(구현 요청이 와도 계획서 근거로 반려): 부정행위 탐지, 공개 크루 검색·모집·소셜 피드, 참가비 징수. 2차로 명시된 것(실시간 위치 공유, 비동기 레이스, 핸디캡, 벌칙 룰, 기프티콘 API, Redis)은 MVP에서 선구현하지 않는다.
