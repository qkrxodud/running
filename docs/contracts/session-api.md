# session-api 계약

> **v0.2 · 2026-07-04 · 명령 확정** — 서버 구현(B2-S2/S3)·앱 소비(B2-C1/C2)는 이 문서 기준(3자 대조의 진실).
> 근거: `22_analyst_design_B2.md` §2·§3(RaceSession 상태머신·Participation), planner A-B5, `domain-model` 스킬 Race 컨텍스트, `V1__init.sql`. 공통 규약은 `conventions.md`.
>
> **변경 이력**
> - v0.2.1 (2026-07-04, domain-analyst): **W46-2/R-007 정합** — §6 start 오류코드 자기모순 제거(403 "참가자 아님" → **403 크루 비멤버 / 409 멤버 미등록** 배타 분리). track-api §1과 동일 규칙(참가자 액션 오류 모델 통일). 응답 shape 무변경.
> - v0.2 (2026-07-04, domain-analyst): 배치 B2 — 조회 세트(생성/목록/상세) shape 확정 유지 + 명령 append(§4 open·§5 register·§6 start·§7 cancel). 상태 전이 매트릭스·오류코드 전수. 세션 생성에 `upload_deadline > scheduled_at` 검증 추가. OPEN 발행=별도 open 명령(생성 즉시 DRAFT), RUNNING 진입=첫 STARTED 신호. 응답 shape 변경 없음(v0.1 호환).
> - v0.1 (2026-07-04, domain-analyst): 계약 우선 초안(조회 세트만).

모든 엔드포인트 `auth: required`.

## Enum 값 집합 (반드시 명시 — 회귀 R-001 방지, 클라 미지값 크래시 금지·unknown 폴백)

- `race_session.status` (**RaceStatus**): {`DRAFT`, `OPEN`, `RUNNING`, `FINALIZING`, `COMPLETED`, `CANCELLED`}
  - 전이: `DRAFT →(open) OPEN →(첫 STARTED) RUNNING →(M2) FINALIZING →(M2) COMPLETED`. `DRAFT|OPEN|RUNNING →(cancel) CANCELLED`. COMPLETED는 전원 업로드 완료 또는 `upload_deadline` 경과 시에만(M2). 크루장은 RUNNING 중에도 CANCELLED 가능. **B2 구현 전이**: 생성(→DRAFT)·open·start(→RUNNING)·cancel. FINALIZING/COMPLETED는 M2. 합법/불법 매트릭스는 §8.
- `participation.status` (**Participation**): {`REGISTERED`, `STARTED`, `FINISHED`, `DNF`, `DNS`, `WITHDRAWN`}
  - 이는 **서버 상태**다. 클라이언트 로컬 상태머신(`READY/RUNNING/FINISHED_LOCAL/UPLOADED`)과 별개 — 계약·응답에 클라 상태를 넣지 않는다.
  - **B2 능동 전이**: `REGISTERED`(register)·`STARTED`(start). `WITHDRAWN`은 유저 탈퇴 시 행 보존 표시(nickname 익명화). `FINISHED`/`DNF`/`DNS`는 **M2**(업로드·FinishPolicy·마감 판정) — 값 집합엔 지금 포함(클라 폴백 대비).

## 오류 코드 (본 API 전수)

`VALIDATION_ERROR`(400) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `CREW_CLOSED`(409) · `SESSION_STATE_INVALID`(409)

---

## 1. POST /api/v1/crews/{crewId}/sessions — 세션 생성

**크루장 전용**. 발행된(세션에서 사용된) 코스는 불변이므로 `course_id` 참조만.

### 요청
```json
{
  "course_id": 55,
  "scheduled_at": "2026-07-10T21:00:00Z",
  "upload_deadline": "2026-07-11T09:00:00Z"
}
```
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| course_id | int64 | 필수 | 같은 크루 소유 코스 |
| scheduled_at | datetime | 필수(NN) | UTC |
| upload_deadline | datetime | 필수(NN) | UTC. `upload_deadline > scheduled_at`(도메인 검증, 위반 시 400). **UX 기본값**: 클라/앱레이어가 `scheduled_at + 12h`를 기본 제시(도메인 하드코딩 금지) |

### 응답 201
세션 상세(§3 SessionDetail). 생성 시 `status = DRAFT`(발행 전 준비 상태 — OPEN은 별도 §4 open 명령).

### 오류
- `400 VALIDATION_ERROR` — `upload_deadline ≤ scheduled_at`, 시각 누락.
- `403 FORBIDDEN` — 크루장 아님.
- `404 NOT_FOUND` — course/crew 없음.
- `409 CREW_CLOSED` — CLOSED 크루.
- `409 SESSION_STATE_INVALID` — 코스가 다른 크루 소속 등.

---

## 2. GET /api/v1/crews/{crewId}/sessions — 세션 목록

크루의 세션 목록. 페이지네이션 래퍼(`conventions.md` §6). 최신 `scheduled_at` 내림차순 기본.

### 응답 200
```json
{
  "items": [
    {
      "id": 91,
      "crew_id": 12,
      "course_id": 55,
      "course_name": "한강 5K",
      "status": "OPEN",
      "scheduled_at": "2026-07-10T21:00:00Z",
      "upload_deadline": "2026-07-11T09:00:00Z",
      "participant_count": 6
    }
  ],
  "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | session id |
| crew_id | int64 | |
| course_id | int64 | |
| course_name | string | 편의 조회용(코스 불변이라 안정) |
| status | string(enum) | RaceStatus |
| scheduled_at | datetime | UTC |
| upload_deadline | datetime | UTC |
| participant_count | int | REGISTERED 이상 참가자 수 |

---

## 3. GET /api/v1/sessions/{sessionId} — 세션 상세 (참가자 상태 포함)

### 응답 200 (SessionDetail)
```json
{
  "id": 91,
  "crew_id": 12,
  "course": {
    "id": 55,
    "name": "한강 5K",
    "distance_m": 5000,
    "route_polyline": "…encoded…",
    "start_lat": 37.5, "start_lng": 127.0,
    "finish_lat": 37.52, "finish_lng": 127.02
  },
  "status": "RUNNING",
  "scheduled_at": "2026-07-10T21:00:00Z",
  "upload_deadline": "2026-07-11T09:00:00Z",
  "participants": [
    { "user_id": 3, "nickname": "민수", "status": "FINISHED" },
    { "user_id": 7, "nickname": "지현", "status": "STARTED" },
    { "user_id": 9, "nickname": "탈퇴한 러너", "status": "DNS" }
  ]
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | |
| crew_id | int64 | |
| course | object | 코스 요약. `id int64`, `name`, `distance_m int`, `route_polyline string`(인코딩), `start/finish_lat/lng double` |
| status | string(enum) | RaceStatus |
| scheduled_at | datetime | UTC |
| upload_deadline | datetime | UTC |
| participants | array | 요소: `user_id int64`, `nickname string`, `status string(enum)` = Participation |

- 탈퇴 유저 참가자는 `nickname`을 익명 표시(예 `"탈퇴한 러너"`)하되 **행은 보존**(user_id 유지) — 리플레이/기록 정합. 익명화 형식은 설계문서 M-4(제안: `탈퇴한 러너` 고정).

### 오류
- `403 FORBIDDEN` — 비멤버 조회.
- `404 NOT_FOUND` — 없는 sessionId.

---

## 4. POST /api/v1/sessions/{sessionId}/open — 세션 발행 (DRAFT → OPEN)

**크루장 전용**. body 없음. DRAFT→OPEN 전이. OPEN 이후 **코스 참조 발행 잠금**(course-api 발행 후 불변) + **참가(register) 개방**.

### 응답 200
세션 상세(§3, `status = OPEN`).

### 오류
- `403 FORBIDDEN` — 크루장 아님.
- `404 NOT_FOUND` — 세션 없음.
- `409 SESSION_STATE_INVALID` — DRAFT 아님(이미 OPEN 이상 또는 종료).
- `409 CREW_CLOSED` — CLOSED 크루.

---

## 5. POST /api/v1/sessions/{sessionId}/register — 참가 신청 (opt-in)

**크루 ACTIVE 멤버 본인**. body 없음. 호출자를 `REGISTERED`로 등록(명시적 opt-in — DNS="신청 후 미출주" 의미론 성립). **OPEN 세션만**. **멱등** — 이미 REGISTERED/STARTED면 no-op 200.

### 응답 200
세션 상세(§3). 호출자 participation이 `participants`에 REGISTERED로 반영.

### 오류
- `403 FORBIDDEN` — 크루 비멤버.
- `404 NOT_FOUND` — 세션 없음.
- `409 SESSION_STATE_INVALID` — OPEN 아님(DRAFT엔 신청 불가, RUNNING 후 지참 차단).

---

## 6. POST /api/v1/sessions/{sessionId}/start — 시작 신호 (STARTED, 멱등)

**참가자 본인**. body 없음. 호출자 participation을 `STARTED`로. **선 register 필요**(participation 부재 시 409). 세션 OPEN/RUNNING만. **멱등** — 이미 STARTED/FINISHED면 no-op 200. **최초 STARTED가 세션 OPEN→RUNNING 전이**.

> 보조 신호 — "지금 뛰는 중" 표시·RUNNING 전이용. **서버 다운 시 유실 무해**(주행 진실은 M2 track_record.started_at). 클라 트래킹 실배선은 M2(D-1).

### 응답 200
세션 상세(§3). 호출자 STARTED, 최초면 세션 `status = RUNNING`.

### 오류 (평가 순서 — W46-2/R-007 정합: 403 크루경계 → 409 상태. 배타)
- `403 FORBIDDEN` — **세션 소유 크루의 ACTIVE 멤버 아님**(비멤버에게 세션 존재·상태 누설 금지).
- `404 NOT_FOUND` — 세션 없음.
- `409 SESSION_STATE_INVALID` — **크루 멤버지만** participation 부재(선 register 필요) 또는 세션이 OPEN/RUNNING 아님.

---

## 7. POST /api/v1/sessions/{sessionId}/cancel — 세션 취소

**크루장 전용**. body 없음. `DRAFT|OPEN|RUNNING → CANCELLED`(RUNNING 중에도 가능). **순위·보상 미생성**. participation 행 미변경(현 상태 보존 — 세션 CANCELLED이므로 무의미해지나 이력 보존). B2 시점 트랙 없음 → 뛰던 참가자 트랙 개인기록 보존은 M2 트랙 등장 시 유효.

### 응답 200
세션 상세(§3, `status = CANCELLED`).

### 오류
- `403 FORBIDDEN` — 크루장 아님.
- `404 NOT_FOUND` — 세션 없음.
- `409 SESSION_STATE_INVALID` — 이미 종료(COMPLETED/CANCELLED) 또는 FINALIZING(M2).

---

## 8. 상태 전이 매트릭스 (합법 ✅ / 불법 = 409 SESSION_STATE_INVALID)

| 명령 \ status | DRAFT | OPEN | RUNNING | FINALIZING | COMPLETED | CANCELLED |
|---|---|---|---|---|---|---|
| open | ✅→OPEN | 409 | 409 | 409 | 409 | 409 |
| register | 409 | ✅ | 409 | 409 | 409 | 409 |
| start | 409 | ✅(첫→RUNNING) | ✅(멱등) | 409 | 409 | 409 |
| cancel | ✅→CANCELLED | ✅→CANCELLED | ✅→CANCELLED | 409(M2) | 409 | 409 |

- FINALIZING→COMPLETED·마감 스케줄러는 **M2**. B2는 종료 전 3상태(DRAFT/OPEN/RUNNING)까지.

---

## 범위 밖 (여기 명시만 — M2/이후)

- 트랙 업로드, 결과·순위 조회, PB, 세션 마감 스케줄러(전원완료/deadline → FINISHED/DNF/DNS·COMPLETED), 리플레이 알림은 **M2**. 본 문서는 세션 CRUD + 참가/시작/취소 명령까지. 추가 시 변경 이력 주석과 함께 append.
