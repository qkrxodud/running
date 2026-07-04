# auth-api 계약

> **v0.1.1 · 2026-07-05** — 서버 구현과 앱 인증 플로우는 이 문서 기준.
> 근거: `12_analyst_design_B.md` §2(JWT 스펙 확정), `62_analyst_design_M2C.md` §0(kapi 장애 코드), planner B1-S1/S5. 공통 규약은 `conventions.md`(v0.1.3).
> **스텁·실 카카오 공존**: local/dev 프로필은 스텁 검증기, prod는 실 카카오 검증기로 **프로필 분기 공존**(§4). 카카오 앱 키 확보 시 실 어댑터 활성.
>
> **변경 이력**
> - v0.1.1 (2026-07-05, domain-analyst): M2-C 보류분 정리 — §1 오류에 `503 AUTH_KAKAO_UNAVAILABLE`(kapi 장애, 재시도 대상 — 401과 분리) 추가. §4 스텁 문구 최신화(삭제→프로필 분기 공존, 스텁은 kapi 미호출이라 503 미발생).
> - v0.1 (2026-07-04, domain-analyst): 계약 우선 초안. JWT HS256 access 30분/refresh 30일 쌍회전, login/refresh/401 규약, 스텁 모드.

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
- `401 AUTH_KAKAO_TOKEN_INVALID` — 카카오 토큰 **검증 실패**(만료·위조 — 사용자 자격 문제). **클라: 재로그인**.
- `503 AUTH_KAKAO_UNAVAILABLE` — 카카오 kapi **서버 장애**(다운·타임아웃·5xx — 검증 자체 불가). **클라: 사용자 탓 아님, 잠시 후 재시도**(재로그인 유도 금지 — 무한 루프 방지). 401과 의미론 분리(conventions §4 v0.1.3).
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

## 4. 검증기 프로필 분기 (스텁 ↔ 실 카카오 공존)

카카오 토큰 검증기는 **프로필 분기로 공존**한다 — 카카오 앱 키 확보 여부와 무관하게 계약(§1~§3)은 불변, 포트 뒤 구현만 교체:

- **local/dev**: `StubKakaoTokenVerifier`.
  - `kakao_access_token` 형식 **`stub:{fake_kakao_id}`**(예 `stub:dev-user-1`) → 해당 fake id의 KakaoAccount로 수용. 같은 fake id 재로그인 = 같은 User(실카카오와 동일 의미론).
  - 그 외 문자열 전부 `401 AUTH_KAKAO_TOKEN_INVALID`.
  - **스텁은 kapi를 호출하지 않으므로 `503 AUTH_KAKAO_UNAVAILABLE` 미발생**(장애 경로 없음). 503은 실 카카오 검증기 활성 시에만 발생.
- **prod**: `RealKakaoTokenVerifier`(카카오 앱 키 필요). kapi 호출 → 검증 실패는 401, kapi 장애(다운·타임아웃·5xx)는 **503 AUTH_KAKAO_UNAVAILABLE**.
  - **prod에는 스텁 빈이 없어**(프로필 미주입) `stub:` 토큰이 통하지 않는다 — 스텁이 운영에 노출될 수 없다(fail-safe).
- 클라 dev 로그인 화면(B1-C2)은 `stub:` 형식으로 전송(dev 빌드 한정). prod 빌드는 카카오 SDK 실 토큰.
- **교체는 포트 뒤 어댑터 스위칭** — §1~§3 계약 무변경. (v0.1 "이 절만 삭제" 표현은 폐기 — 스텁은 dev 자산으로 상시 공존.)
