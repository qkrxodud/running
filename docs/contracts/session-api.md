# session-api 계약

> **v0.1 · 2026-07-04 · 계약 우선 초안(contract-first)** — 서버 구현이 아직 없어도 이 shape가 진실이다. 명령(세션 생성)은 계약만 확정, 구현은 배치 B.
> 근거: planner A-B5, `domain-model` 스킬 Race 컨텍스트. 공통 규약은 `conventions.md`.

모든 엔드포인트 `auth: required`.

## Enum 값 집합 (반드시 명시 — 회귀 R-001 방지)

- `race_session.status` (**RaceStatus**): {`DRAFT`, `OPEN`, `RUNNING`, `FINALIZING`, `COMPLETED`, `CANCELLED`}
  - 전이: `DRAFT → OPEN → RUNNING → FINALIZING → COMPLETED | CANCELLED`. COMPLETED는 전원 업로드 완료 또는 `upload_deadline` 경과 시에만. 크루장은 RUNNING 중에도 CANCELLED 가능.
- `participation.status` (**Participation**): {`REGISTERED`, `STARTED`, `FINISHED`, `DNF`, `DNS`, `WITHDRAWN`}
  - 이는 **서버 상태**다. 클라이언트 로컬 상태머신(`READY/RUNNING/FINISHED_LOCAL/UPLOADED`)과 별개 — 계약·응답에 클라 상태를 넣지 않는다.

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
| upload_deadline | datetime | 필수(NN) | UTC. **미규정 — 제안**: 클라/앱레이어가 `scheduled_at + 12h`를 기본 제시(도메인 하드코딩 금지, UX 기본값) |

### 응답 201
세션 상세(§3 SessionDetail). 생성 시 `status = DRAFT`.

### 오류
- `403 FORBIDDEN` — 크루장 아님.
- `404 NOT_FOUND` — course/crew 없음.
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

## 배치 A 범위 밖 (여기 명시만, 계약 상세는 배치 B/이후)

- 세션 참가(REGISTER), '레이스 시작' STARTED 신호, 트랙 업로드, 결과·순위 조회, 세션 취소는 **배치 A 계약 초안에서 제외**. 본 문서는 생성/목록/상세(조회 세트)만 확정한다. 추가 시 이 파일에 변경 이력 주석과 함께 append.
