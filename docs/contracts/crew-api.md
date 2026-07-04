# crew-api 계약

> **v0.1 · 2026-07-04 · 계약 우선 초안(contract-first)** — 서버 구현이 아직 없어도 이 shape가 진실이다. 명령 세트(생성/코드생성/참가)는 계약만 확정, 구현은 배치 B.
> 근거: planner A-B5, `domain-model` 스킬 Crew 컨텍스트. 공통 규약(인증·오류·시각·페이지네이션)은 `conventions.md`.

모든 엔드포인트 `auth: required` (`Authorization: Bearer {token}`).

## Enum 값 집합

- `crew.status`: {`ACTIVE`, `CLOSED`} — 마지막 1인 탈퇴 시 CLOSED.
- `crew_member.role`: {`LEADER`, `MEMBER`} — 크루당 ACTIVE LEADER 정확히 1명.
- `crew_member.status`: {`ACTIVE`, `WITHDRAWN`}.

---

## 1. POST /api/v1/crews — 크루 생성

생성자가 자동으로 `LEADER`. 크루장 항상 1명 불변식의 시작점.

### 요청
```json
{ "name": "새벽 러닝크루" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| name | string | 필수, 1~50자 |

### 응답 201
크루 상세(아래 §3 CrewDetail shape)를 반환. 생성자는 `leader_id`이자 유일 멤버(role=LEADER).

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
| members | array | ACTIVE 멤버. 요소: `user_id int64`, `nickname string`, `role string(enum)`, `joined_at datetime` |

- 탈퇴 멤버는 `members`에 미포함(익명 보존은 리플레이/순위 파생물 한정, 크루 명단엔 노출 안 함).

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
| max_uses | int | 필수, ≥1 | 최대 사용 횟수 |
| expires_in_hours | int | 필수, ≥1 | 서버가 `expires_at = now(UTC) + hours` 계산. **미규정 — 제안**: 앱레이어 기본값(예 72h), 도메인 하드코딩 금지 |

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
| code | string | 초대 코드(자연키) |
| crew_id | int64 | |
| expires_at | datetime | UTC |
| max_uses | int | |
| used_count | int | 생성 시 0 |

### 오류
- `403 FORBIDDEN` — 크루장 아님.
- `409 CREW_CLOSED` — CLOSED 크루.

---

## 5. POST /api/v1/crews/join — 초대 코드로 참가

인증 사용자를 코드가 가리키는 크루에 `MEMBER`로 추가.

### 요청
```json
{ "code": "K7QF2A" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| code | string | 필수 |

### 응답 200
참가한 크루 상세(§3 CrewDetail). 서버는 `used_count += 1`.

### 오류
- `404 INVITE_CODE_INVALID` — 없는 코드.
- `409 INVITE_CODE_EXPIRED` — `expires_at` 경과(UTC 판정).
- `409 INVITE_CODE_EXHAUSTED` — `used_count >= max_uses`.
- `409 ALREADY_JOINED` — 이미 ACTIVE 멤버.
- `409 CREW_CLOSED` — CLOSED 크루.

> 불변식(코드 강제, DB 아님): `used_count <= max_uses` / 만료 판정 / 중복가입 방지. 참가 유스케이스 트랜잭션에서 검증(설계문서 §4.2 IC-1).
