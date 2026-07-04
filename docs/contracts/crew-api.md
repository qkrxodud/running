# crew-api 계약

> **v0.2 · 2026-07-04 · 명령 상세 확정** — 서버 구현(B1-S4)·앱 소비(B1-C5)는 이 문서 기준(3자 대조의 진실).
> 근거: `12_analyst_design_B.md` §3(Crew 애그리거트·승계·InviteCode), domain-model 스킬 Crew 불변식. 공통 규약은 `conventions.md`.
>
> **변경 이력**
> - v0.2 (2026-07-04, domain-analyst): 배치 B1 명령 상세 확정 — 초대코드 형식(6자, 혼동문자 제외)·`expires_in_hours` 기본값 72h(앱레이어) 확정, WITHDRAWN 멤버 재가입=기존 행 복원 의미론 명시, 크루장 승계 tie-break(id 오름차순) 명시, CrewMemberJoined 인앱 갈음(O-1) 주석, 오류 코드 전수 확정. 응답 shape 변경 없음(v0.1 호환).
> - v0.1 (2026-07-04, domain-analyst): 계약 우선 초안.

모든 엔드포인트 `auth: required` (`Authorization: Bearer {access_token}` — 401 규약은 auth-api.md §3).

## Enum 값 집합 (R-001 방지 — 클라는 미지값 크래시 금지, unknown 폴백+로깅)

- `crew.status`: {`ACTIVE`, `CLOSED`} — 마지막 1인 탈퇴 시 CLOSED. CLOSED 크루엔 모든 명령 409.
- `crew_member.role`: {`LEADER`, `MEMBER`} — 크루당 ACTIVE LEADER 정확히 1명(크루장 탈퇴 시 가입일 최선임 자동 승계, 동률은 id 오름차순).
- `crew_member.status`: {`ACTIVE`, `WITHDRAWN`}.

## 오류 코드 (본 API 전수)

`VALIDATION_ERROR`(400) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `INVITE_CODE_INVALID`(404) · `INVITE_CODE_EXPIRED`(409) · `INVITE_CODE_EXHAUSTED`(409) · `ALREADY_JOINED`(409) · `CREW_CLOSED`(409)

---

## 1. POST /api/v1/crews — 크루 생성

생성자가 자동으로 `LEADER`(leader_id + LEADER 멤버십이 같은 트랜잭션에서 생성). 크루장 항상 1명 불변식의 시작점.

### 요청
```json
{ "name": "새벽 러닝크루" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| name | string | 필수, trim 후 1~50자 |

### 응답 201
크루 상세(§3 CrewDetail). 생성자는 `leader`이자 유일 멤버.

### 오류
- `400 VALIDATION_ERROR` — name 길이/누락.

---

## 2. GET /api/v1/crews — 내 크루 목록

인증 사용자가 ACTIVE 멤버인 크루 목록. 페이지네이션 래퍼(`conventions.md` §6).

### 응답 200
```json
{
  "items": [
    {
      "id": 12,
      "name": "새벽 러닝크루",
      "status": "ACTIVE",
      "member_count": 8,
      "role": "MEMBER",
      "created_at": "2026-06-01T12:00:00Z"
    }
  ],
  "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | crew id |
| name | string | |
| status | string(enum) | {ACTIVE, CLOSED} |
| member_count | int | ACTIVE 멤버 수 |
| role | string(enum) | 요청자의 역할 {LEADER, MEMBER} |
| created_at | datetime | UTC |

---

## 3. GET /api/v1/crews/{crewId} — 크루 상세

멤버 전용. **합류 알림의 인앱 갈음 지점**(O-1 확정) — 별도 알림 API·알림함 없음, `members`의 `joined_at`으로 최근 합류가 노출된다.

### 응답 200 (CrewDetail)
```json
{
  "id": 12,
  "name": "새벽 러닝크루",
  "status": "ACTIVE",
  "leader": { "user_id": 3, "nickname": "민수" },
  "created_at": "2026-06-01T12:00:00Z",
  "members": [
    { "user_id": 3, "nickname": "민수", "role": "LEADER", "joined_at": "2026-06-01T12:00:00Z" },
    { "user_id": 7, "nickname": "지현", "role": "MEMBER", "joined_at": "2026-06-02T08:30:00Z" }
  ]
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | |
| name | string | |
| status | string(enum) | {ACTIVE, CLOSED} |
| leader | object | `{user_id int64, nickname string}` |
| created_at | datetime | UTC |
| members | array | **ACTIVE 멤버만**, `joined_at` 오름차순. 요소: `user_id int64`, `nickname string`, `role string(enum)`, `joined_at datetime` |

- 탈퇴(WITHDRAWN) 멤버는 미포함 — 익명 보존은 순위·리플레이 파생물 한정, 크루 명단엔 노출하지 않는다.

### 오류
- `403 FORBIDDEN` — 비멤버 조회.
- `404 NOT_FOUND` — 없는 crewId.

---

## 4. POST /api/v1/crews/{crewId}/invite-codes — 초대 코드 생성

**크루장 전용**(공개 가입 경로 없음 — 초대 코드로만).

### 요청
```json
{ "max_uses": 10, "expires_in_hours": 72 }
```
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| max_uses | int | 필수, 1~100 | 최대 사용 횟수 |
| expires_in_hours | int | 필수, 1~720 | 서버가 `expires_at = now(UTC) + hours` 계산. **기본값 72는 클라 UX 기본값**(입력 폼 프리필) — 도메인/서버 하드코딩 아님 |

### 응답 201
```json
{
  "code": "K7QF2A",
  "crew_id": 12,
  "expires_at": "2026-07-07T09:00:00Z",
  "max_uses": 10,
  "used_count": 0
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| code | string | **대문자+숫자 6자, 혼동 문자(0/O/1/I) 제외** — v0.2 확정. 클라 입력 UI는 이 문자 집합 기준 |
| crew_id | int64 | |
| expires_at | datetime | UTC |
| max_uses | int | |
| used_count | int | 생성 시 0 |

### 오류
- `400 VALIDATION_ERROR` — 범위 위반.
- `403 FORBIDDEN` — 크루장 아님.
- `404 NOT_FOUND` — 없는 crewId.
- `409 CREW_CLOSED` — CLOSED 크루.

> B1 범위: 코드 문자열 표시 + 클립보드까지. 카톡 공유·딥링크는 도메인/카카오 발급물 대기.

---

## 5. POST /api/v1/crews/join — 초대 코드로 참가

인증 사용자를 코드가 가리키는 크루에 `MEMBER`로 추가. 성공 시 서버는 `used_count += 1`, `CrewMemberJoined` 이벤트 발행(푸시 없음 — 인앱 갈음, O-1).

### 요청
```json
{ "code": "K7QF2A" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| code | string | 필수. 대소문자 무시 비교(서버가 대문자 정규화) — v0.2 확정 |

### 응답 200
참가한 크루 상세(§3 CrewDetail).

### 동작 상세 (v0.2 확정)
- 검증 순서(단일 트랜잭션, 코드 행 잠금): 코드 존재 → 만료(UTC) → 횟수 → 크루 ACTIVE → 멤버십.
- **과거 WITHDRAWN 멤버의 재참가 = 기존 멤버십 행 복원**(status ACTIVE, `joined_at` 재참가 시각으로 갱신, role MEMBER) — 크루장 승계 서열도 재참가 시각 기준. `UQ(crew_id, user_id)` 제약과 정합.

### 오류
- `404 INVITE_CODE_INVALID` — 없는 코드.
- `409 INVITE_CODE_EXPIRED` — `expires_at` 경과(UTC 판정).
- `409 INVITE_CODE_EXHAUSTED` — `used_count >= max_uses`.
- `409 ALREADY_JOINED` — 이미 ACTIVE 멤버.
- `409 CREW_CLOSED` — CLOSED 크루.

> 불변식(코드 강제, DB 아님): `used_count <= max_uses` / 만료 판정 / 크루장 정확히 1명·승계 — `12_analyst_design_B.md` §3.4 체크리스트 참조.
