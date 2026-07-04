# auth-api 계약

> **v0.1 · 2026-07-04 · 계약 우선 초안(contract-first)** — 서버 구현(B1-S1)과 앱 인증 플로우(B1-C2)는 이 문서 기준.
> 근거: `12_analyst_design_B.md` §2(JWT 스펙 확정), planner B1-S1/S5. 공통 규약은 `conventions.md`.
> **스텁 모드**: M0 카카오 앱 키 미확보 — 실 카카오 검증은 대기, local/dev 프로필은 스텁 검증기로 동작(§4).

## 토큰 개요 (확정 스펙)

- 자체 발급 **JWT HS256** (시크릿: 서버 env `JWT_SECRET`).
- **access 30분 / refresh 30일**. 갱신 시 쌍 회전(둘 다 재발급).
- 클레임: `sub`(내부 user id 문자열), `typ`(`access`|`refresh`), `iat`, `exp`, `jti`(refresh만). **kakao_id·닉네임 등 개인정보 클레임 금지.**
- 보호 API 호출: `Authorization: Bearer {access_token}`.
- 인증 불요 경로: `/api/v1/app-version`, `/api/v1/auth/**`, `/actuator/health`. 그 외 전부 인증 필요.
- WITHDRAWN 사용자는 유효 서명 토큰이어도 **401** (서버가 매 요청 상태 확인).

---

## 1. POST /api/v1/auth/login — 카카오 토큰 → 자체 JWT 발급

인증 불요. 카카오 액세스 토큰을 검증하고 자체 토큰 쌍 발급. 해당 카카오 계정의 User가 없으면 **신규 생성**(placeholder 닉네임, 온보딩 미완료). 탈퇴 후 재로그인도 신규 User 생성이다(kakao_id 파기 구조의 귀결 — 과거 기록과 분리).

### 요청
```json
{ "kakao_access_token": "…", "client_meta": { "app_version": "1.0.0" } }
```
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| kakao_access_token | string | 필수 | 카카오 SDK가 발급한 액세스 토큰 (스텁 모드는 §4 형식) |
| client_meta | object? | 선택 | 디버깅용 자유 형식 — 서버는 저장/로깅만, 판정에 사용 금지(conventions §8) |

### 응답 200
```json
{
  "access_token": "eyJ…",
  "refresh_token": "eyJ…",
  "token_type": "Bearer",
  "expires_in": 1800,
  "is_new_user": true,
  "user": { "id": 3, "nickname": "러너7401", "onboarding_completed": false }
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| access_token / refresh_token | string | JWT |
| token_type | string | 고정 `"Bearer"` |
| expires_in | int | access 잔여 수명(초) = 1800 |
| is_new_user | bool | 이번 호출로 User가 생성됨 |
| user | object | `id int64`, `nickname string`(placeholder 가능), `onboarding_completed bool` — false면 클라는 온보딩(닉네임 설정)으로 |

### 오류
- `401 AUTH_KAKAO_TOKEN_INVALID` — 카카오 토큰 검증 실패(만료·위조 포함).
- `400 VALIDATION_ERROR` — 필드 누락.

---

## 2. POST /api/v1/auth/refresh — 토큰 갱신 (쌍 회전)

인증 불요(바디의 refresh가 자격). 유효한 refresh → **access+refresh 새 쌍** 발급.

### 요청
```json
{ "refresh_token": "eyJ…" }
```

### 응답 200
`login`과 동일 shape에서 `is_new_user`·`user` 제외:
```json
{ "access_token": "eyJ…", "refresh_token": "eyJ…", "token_type": "Bearer", "expires_in": 1800 }
```

### 오류
- `401 AUTH_REFRESH_INVALID` — 만료·위조·`typ != refresh`·WITHDRAWN 사용자. → **클라: 토큰 폐기 후 재로그인.**
- `400 VALIDATION_ERROR` — 필드 누락.

주의: 구 refresh의 서버측 폐기는 없음(무상태). 클라는 회전 응답 수신 즉시 구 쌍을 덮어쓴다.

---

## 3. 401 규약 (보호 API 공통 — 클라 플로우)

| code | 의미 | 클라 행동 |
|---|---|---|
| `AUTH_TOKEN_EXPIRED` | access 만료 | `/auth/refresh` 1회 → 원요청 재시도. refresh도 실패 시 재로그인 |
| `UNAUTHORIZED` | 토큰 부재·위조·WITHDRAWN | 즉시 토큰 폐기·재로그인 |
| `AUTH_REFRESH_INVALID` | (refresh 엔드포인트 전용) 갱신 불가 | 토큰 폐기·재로그인 |

- 재시도는 요청당 **1회**만(무한 갱신 루프 금지).
- shape는 conventions §4 `{code, message}` 동일.

---

## 4. 스텁 모드 (local/dev 프로필 전용)

M0 카카오 앱 키 확보 전까지 서버 local/dev 프로필은 `StubKakaoTokenVerifier`로 동작한다:

- `kakao_access_token` 형식: **`stub:{fake_kakao_id}`** (예: `stub:dev-user-1`) → 해당 fake id의 KakaoAccount로 수용. 같은 fake id 재로그인 = 같은 User(실카카오와 동일 의미론).
- 그 외 문자열 전부 `401 AUTH_KAKAO_TOKEN_INVALID`.
- **prod/프로필 미지정에서는 스텁 빈이 없어 부팅 실패(fail-fast)** — 스텁이 운영에 노출될 수 없다.
- 클라 dev 로그인 화면(B1-C2)은 이 형식으로 전송. prod 빌드엔 미포함.
- 실 카카오 어댑터 배선 시 이 절만 삭제되고 §1~§3은 무변경(포트 뒤 교체).
