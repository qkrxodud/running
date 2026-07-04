# app-version 계약

> **v0.1 · 2026-07-04 · 계약 우선 초안(contract-first)** — 서버 구현이 아직 없어도 이 shape가 진실이다.
> 근거: planner A-B6, `domain-model` 스킬 `app_min_version` 테이블. 공통 규약은 `conventions.md` 참조.

## GET /api/v1/app-version

강제 업데이트 판단용 최소 지원 버전 조회. **인증 불요**(로그인보다 앞서 호출되기 때문).

### 요청

- 쿼리 파라미터: `platform` — `string(enum)` {`ANDROID`, `IOS`}. 필수.
  - 예: `GET /api/v1/app-version?platform=ANDROID`
- `platform`은 클라 식별용 파라미터일 뿐 — 서버 도메인 결합 아님(플랫폼 무지 원칙과 무관).

### 응답 200

```json
{
  "platform": "ANDROID",
  "min_version": "1.2.0",
  "updated_at": "2026-07-01T00:00:00Z"
}
```

| 필드 | 타입 | 비고 |
|---|---|---|
| platform | string(enum) | {ANDROID, IOS} — 요청 echo |
| min_version | string | semver(`major.minor.patch`). 클라 현재 버전 < 이 값이면 강제 업데이트 |
| updated_at | datetime | UTC ISO-8601 |

### 오류

- `400 VALIDATION_ERROR` — `platform` 누락/미지 값.
- `404 NOT_FOUND` — 해당 플랫폼 레코드 없음. **미규정 — 제안**: 클라는 404를 "강제 업데이트 아님"으로 안전 해석(버전 게이트가 서버 미설정으로 앱을 잠그면 안 됨).

### 클라이언트 소비 (참고, 로직은 배치 B)

- 앱 시작 시 1회 호출. `min_version` 미충족 → 업데이트 안내 화면(진행 차단). 충족/오류 → 정상 진입.
- 강제 업데이트 판단 로직 자체는 배치 B(화면 뼈대)에서 구현. 배치 A는 서버 계약·테이블·엔드포인트 골격만.
