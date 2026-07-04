# API 공통 규약 (conventions)

> **v0.1 · 2026-07-04 · 계약 우선 초안(contract-first)** — 서버 구현이 아직 없어도 이 문서가 앱↔서버 공유 스키마의 진실이다. 변경은 domain-analyst 경유, flutter-dev·backend-dev 양쪽 통지.
> 관리자: domain-analyst. 규범: `domain-model` 스킬, 계획서 §5~§7.

## 1. Base Path / 버전

- 모든 API는 `/api/v1` 하위. 예: `/api/v1/crews`.
- 자원 경로는 **복수형 소문자**: `/api/v1/crews/{crewId}/sessions`.
- 하위 자원은 중첩: `/api/v1/crews/{crewId}/invite-codes`.

## 2. 직렬화

- Content-Type: `application/json; charset=utf-8`.
- **JSON 필드는 snake_case** (예: `crew_id`, `scheduled_at`). QA의 계약↔서버↔클라 3자 대조 대상.
- 서버는 **도메인 객체를 직접 직렬화하지 않는다** — 어댑터의 응답 DTO(record)로만.

## 3. 시각 (UTC 규약)

- 서버 저장·비교·판정은 **전부 UTC**. `scheduled_at`, `upload_deadline`, 마감 판정, 리마인더 발송이 모두 UTC 기준.
- 계약의 모든 시각 필드는 **ISO-8601 + UTC(Z 오프셋 명시)** 문자열: `"2026-07-04T09:30:00Z"`.
- **KST 등 표시 변환은 클라이언트 소관.** 서버는 로컬 타임존을 알지 못한다(플랫폼 무지).
- 시각 필드명은 `_at`(시점) / `_deadline` 접미.

## 4. 오류 응답 형식

모든 오류는 다음 shape로 통일:

```json
{ "code": "COURSE_IMMUTABLE", "message": "이미 발행된 코스는 수정할 수 없습니다." }
```

- `code`: 기계 판독용 상수(UPPER_SNAKE). 클라이언트 분기는 `code`로만 — `message` 문자열 매칭 금지.
- `message`: 사람용(한국어). 표시 문구는 클라가 code로 자체 대응 가능(로케일).
- HTTP 상태 매핑:
  - `400 BAD_REQUEST` — 요청 형식 오류·도메인 규칙 위반(값 범위 등).
  - `401 UNAUTHORIZED` — 토큰 없음/만료 → **클라이언트는 재로그인 플로우 진입**.
  - `403 FORBIDDEN` — 권한 부족(크루장 전용 작업을 멤버가 호출 등).
  - `404 NOT_FOUND` — 자원 없음.
  - `409 CONFLICT` — 상태 충돌(이미 참가함, 만료된 초대코드, 코스 불변 위반 등).
- 공통 `code` 초안(배치 A 범위): `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `INVITE_CODE_INVALID`, `INVITE_CODE_EXPIRED`, `INVITE_CODE_EXHAUSTED`, `ALREADY_JOINED`, `CREW_CLOSED`, `COURSE_IMMUTABLE`, `SESSION_STATE_INVALID`. (배치 B에서 확장)

## 5. 인증

- 방식: **자체 발급 토큰**(카카오 로그인 → 서버가 자체 토큰 발급). 카카오 회원번호는 서버 내부 봉인, 계약·응답에 노출 금지.
- 헤더: `Authorization: Bearer {token}`.
- 토큰 형식·만료·갱신 엔드포인트 상세는 **배치 B에서 확정**(현재 미규정 — 제안: JWT access + refresh).
- `GET /app-version`만 **인증 불요**(강제 업데이트 판단이 로그인보다 앞섬). 그 외 배치 A 조회/명령은 전부 인증 필요(단, 계약 초안이므로 개별 문서에 `auth: required` 명시).

## 6. 페이지네이션

- **미규정 — 제안: offset 기반 단순 방식**(크루/세션 목록 규모가 작음. 히스토리 대량화 시 cursor로 승격).
- 요청 쿼리: `?page={0-base}&size={기본 20, 최대 100}`.
- 응답 공통 래퍼:

```json
{
  "items": [ /* … */ ],
  "page": 0,
  "size": 20,
  "total_elements": 42,
  "total_pages": 3
}
```

- 목록 계약은 이 래퍼로 감싼 `items`를 반환한다(개별 문서에서 `items` 요소 shape 정의).

## 7. 필드 타입 표기 규약(본 계약 문서군 공통)

- `int64` = 서버 PK(BIGINT). JSON에선 number. 클라는 정밀도 손실 없는 정수형으로 파싱(Dart int 64bit OK).
- `string(enum)` = 문자열 enum. **값 집합은 각 문서에 명시**하며 대문자(예: `RUNNING`). 클라는 미지 값 수신 시 크래시 금지 — 알 수 없는 값은 안전 기본으로 폴백하고 로깅(회귀 R-001 방지).
- `datetime` = §3 UTC ISO-8601 문자열.
- `bool`, `int`, `double`, `string` = 통상 의미.
- nullable 필드는 각 문서에서 `?` 표기.

## 8. 플랫폼 무지

- 계약에 플랫폼 종속 필드 금지. 예외는 디버깅용 `client_meta`(자유 형식 객체, 서버는 저장만)뿐 — 도메인 판정에 사용 금지.
