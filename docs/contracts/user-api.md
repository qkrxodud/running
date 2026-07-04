# user-api 계약

> **v0.1 · 2026-07-04 · 계약 우선 초안(contract-first)** — 서버(B1-S2/S3)·앱(B1-C2/C4)은 이 문서 기준.
> 근거: `12_analyst_design_B.md` §1(User 애그리거트·탈퇴 절차·온보딩 V2), domain-model 스킬 User 불변식. 공통 규약은 `conventions.md`.

모든 엔드포인트 `auth: required`. 자원은 전부 **본인**(`/users/me`) — 타 유저 프로필 조회 API는 B1 범위 아님.

## Enum 값 집합

- `user.status`: {`ACTIVE`, `WITHDRAWN`} — 단, 본 API 응답에서 실질적으로 항상 `ACTIVE`(WITHDRAWN은 401로 도달 불가).
- `platform`: {`ANDROID`, `IOS`} (app-version.md와 동일 집합).

---

## 1. GET /api/v1/users/me — 내 프로필

### 응답 200
```json
{
  "id": 3,
  "nickname": "민수",
  "status": "ACTIVE",
  "onboarding_completed": true,
  "created_at": "2026-07-04T01:00:00Z"
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | 내부 user id (카카오 회원번호는 어떤 응답에도 미노출) |
| nickname | string | 온보딩 전엔 서버 생성 placeholder |
| status | string(enum) | {ACTIVE, WITHDRAWN} |
| onboarding_completed | bool | `onboarded_at != null`. false면 클라는 온보딩 화면으로 |
| created_at | datetime | UTC |

---

## 2. PUT /api/v1/users/me/nickname — 닉네임 설정/수정 (온보딩 겸용)

온보딩 최초 설정과 이후 수정이 같은 엔드포인트. 최초 성공 시 서버가 `onboarded_at` 기록 → 이후 `onboarding_completed = true`.

### 요청
```json
{ "nickname": "민수" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| nickname | string | 필수. trim 후 1~30자, 제어문자 금지. **유일성 없음**(중복 허용 — 식별은 user id) |

### 응답 200
§1 프로필 shape (갱신 반영).

### 오류
- `400 VALIDATION_ERROR` — 길이·형식 위반.

---

## 3. DELETE /api/v1/users/me — 회원 탈퇴

Play 계정 삭제 요건의 진입점. 2단 확인 UI는 클라 소관 — 서버는 즉시 실행.

### 동작 (단일 트랜잭션 — 계약으로 보장하는 효과)
- 즉시 파기: 닉네임(→ `"탈퇴한 러너"` 고정 문자열로 익명화), kakao_id, 디바이스 토큰, 트랙 원본(track_payload).
- 익명 보존: 과거 순위·기록 요약·리플레이 파생물 — 다른 크루원의 히스토리는 깨지지 않는다.
- 크루 효과: 모든 소속 크루에서 탈퇴 처리. 크루장이었다면 가입일 최선임 자동 승계, 마지막 1인이었다면 크루 CLOSED.
- 토큰: 기존 access/refresh 전부 즉시 무효(이후 요청 401 `UNAUTHORIZED`).
- **재로그인 = 신규 계정**: 동일 카카오로 다시 로그인하면 새 User가 생성되며 과거 기록과 연결되지 않는다(복구 불가 — 클라 확인 문구에 반드시 고지).

### 응답 204 (본문 없음)

### 오류
- `409 VALIDATION_ERROR` 아님 — 이미 WITHDRAWN인 토큰은 401로 선차단되므로 별도 409 없음. (동시 요청 경합은 서버가 멱등 처리 — 두 번째 요청도 204 또는 401.)

---

## 4. PUT /api/v1/users/me/device-token — FCM 디바이스 토큰 등록/갱신

서버측 API만 B1 구현 — 클라 토큰 취득(Firebase)은 M3 대기.

### 요청
```json
{ "fcm_token": "fcm-abc…", "platform": "ANDROID" }
```
| 필드 | 타입 | 제약 |
|---|---|---|
| fcm_token | string | 필수, ≤255자 |
| platform | string(enum) | 필수, {ANDROID, IOS} |

### 동작
- **fcm_token 기준 upsert**: 동일 토큰이 이미 있으면 user_id·platform·updated_at 갱신(기기 소유자 변경 대응), 없으면 신규 행.

### 응답 204 (본문 없음)

### 오류
- `400 VALIDATION_ERROR` — 필드 누락·미지 platform 값.
