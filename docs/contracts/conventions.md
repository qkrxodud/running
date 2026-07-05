# API 공통 규약 (conventions)

> **v0.1.1 · 2026-07-04** — 서버 구현이 아직 없어도 이 문서가 앱↔서버 공유 스키마의 진실이다. 변경은 domain-analyst 경유, flutter-dev·backend-dev 양쪽 통지.
> 관리자: domain-analyst. 규범: `domain-model` 스킬, 계획서 §5~§7.
>
> **변경 이력**
> - v0.1.4 (2026-07-05, domain-analyst): M3-A — §10 딥링크 규약 신설(`runningcrew://` 스킴 — 세션/리플레이 경로, FCM data.deep_link 탑재). O-M3-3 확정.
> - v0.1.3 (2026-07-05, domain-analyst): M2-C — §4 상태 매핑에 `503 SERVICE_UNAVAILABLE`(의존성 일시 장애) 추가, code 집합에 `AUTH_KAKAO_UNAVAILABLE`(503 — kapi 장애, auth-api §1), `COURSE_PROMOTION_INELIGIBLE`(409 — 코스 승격 자격 미달, course-api §4) 추가.
> - v0.1.2 (2026-07-04, domain-analyst): M2-A — §4 code 집합에 트랙 업로드/결과 5종 추가(`TRACK_ALREADY_UPLOADED`, `TRACK_PAYLOAD_INVALID`, `TRACK_ARRAY_LENGTH_MISMATCH`, `TRACK_TOO_LARGE`, `RESULT_NOT_READY`), §9 대량 배열 시각 표현 예외 신설(track 업로드 payload의 timestamp 배열은 epoch millis). 상세는 track-api.md가 진실.
> - v0.1.1 (2026-07-04, domain-analyst): 배치 B1 — §4 code 집합에 AUTH_* 3종 추가, §5 인증 미규정 해소(상세는 auth-api.md가 진실), §6 페이지네이션 offset 방식 확정.
> - v0.1 (2026-07-04, domain-analyst): 계약 우선 초안.

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
  - `401 UNAUTHORIZED` — 인증 실패. code별 클라 행동(만료→갱신 vs 재로그인)은 auth-api.md §3 규약.
  - `403 FORBIDDEN` — 권한 부족(크루장 전용 작업을 멤버가 호출 등).
  - `404 NOT_FOUND` — 자원 없음.
  - `409 CONFLICT` — 상태 충돌(이미 참가함, 만료된 초대코드, 코스 불변 위반, 승격 자격 미달 등).
  - `413 PAYLOAD_TOO_LARGE` — 요청 본문 상한 초과(track 업로드 크기 — track-api §0).
  - `503 SERVICE_UNAVAILABLE` — **외부 의존성 일시 장애**(예 카카오 kapi 다운). "당신 자격 문제"(401)와 구분 — 클라 행동은 **재입력·재로그인이 아니라 잠시 후 재시도**.
- 공통 `code` 집합: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `INVITE_CODE_INVALID`, `INVITE_CODE_EXPIRED`, `INVITE_CODE_EXHAUSTED`, `ALREADY_JOINED`, `CREW_CLOSED`, `COURSE_IMMUTABLE`, `SESSION_STATE_INVALID` + **v0.1.1 추가(401 세분)**: `AUTH_KAKAO_TOKEN_INVALID`, `AUTH_TOKEN_EXPIRED`, `AUTH_REFRESH_INVALID` (의미·클라 플로우는 auth-api.md §3) + **v0.1.2 추가(M2 트랙/결과)**: `TRACK_ALREADY_UPLOADED`(409 — 동일 participation 재업로드, 다른 내용), `TRACK_PAYLOAD_INVALID`(400 — 폴리라인 디코딩 실패·시간 역순/미래), `TRACK_ARRAY_LENGTH_MISMATCH`(400 — 병렬 배열 길이 불일치), `TRACK_TOO_LARGE`(413 — 크기 상한 초과), `RESULT_NOT_READY`(409 — 결과 미확정 세션의 결과 조회) + **v0.1.3 추가(M2-C)**: `AUTH_KAKAO_UNAVAILABLE`(503 — 카카오 kapi 장애, 재시도 대상. auth-api §1), `COURSE_PROMOTION_INELIGIBLE`(409 — 코스 승격 소스 트랙 자격 미달: 미완주·거리 하한 미달. course-api §4). 의미는 각 문서. (이후 배치에서 확장)

## 5. 인증

- 방식: **자체 발급 토큰**(카카오 로그인 → 서버가 자체 토큰 발급). 카카오 회원번호는 서버 내부 봉인, 계약·응답에 노출 금지.
- 헤더: `Authorization: Bearer {token}`.
- 토큰 형식·만료·갱신·401 플로우 상세는 **`auth-api.md`가 진실**(v0.1.1에서 미규정 해소 — JWT HS256, access 30분/refresh 30일, 쌍 회전).
- 인증 불요 경로: `GET /app-version`, `/api/v1/auth/**`, `/actuator/health`. 그 외 전부 인증 필요(개별 문서에 `auth: required` 명시).

## 6. 페이지네이션

- **offset 기반 단순 방식 확정**(v0.1.1 — 크루/세션 목록 규모가 작음. 히스토리 대량화 시 cursor로 승격 검토).
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
- `client_meta` 허용 키는 `{os, os_version, device_model}` **3개로 고정**(계획서 §6). 서버는 저장만 하고 완주·순위·마감 판정에 절대 사용 안 함.

## 9. 대량 배열 시각 표현 예외 (v0.1.2)

- §3(ISO-8601 문자열)은 **자원 시각 필드**(`scheduled_at`, `started_at`, `finished_at` 등 단일 시점) 규약이다.
- 트랙 업로드 payload의 **`timestamps` 배열**처럼 수백~수천 원소의 대량 시각열은 예외로 **epoch milliseconds (int64, UTC 기준)** 를 쓴다 — 문자열 파싱 비용·전송 크기 절감. GPS 시각 우선 원칙은 동일 적용(§track-api). 이 예외는 track 업로드 병렬 배열에만 국한한다.
- **리플레이 스냅샷 payload의 `t_ms`(상대 경과 시각)**도 동일 취지로 **정수 밀리초**를 쓴다 — 단 이는 절대시각(epoch)이 아니라 **각자 시작 t=0 기준 상대 경과**(replay-api §2). 절대 시점 필드(finalized_at 등)는 §3 ISO-8601 유지.

## 10. 딥링크 규약 (v0.1.4 — O-M3-3)

- **스킴: `runningcrew://`** (Android/iOS 커스텀 스킴).
- 경로 매핑(FCM 알림 탭·외부 진입 → go_router):
  | 딥링크 URI | 화면 | go_router 경로 |
  |---|---|---|
  | `runningcrew://session/{sessionId}` | 세션 상세 | `/sessions/:id` |
  | `runningcrew://replay/{sessionId}` | 리플레이 뷰어 | `/sessions/:id/replay` |
- **리플레이 딥링크 키 = `sessionId`**(snapshotId 아님) — 스냅샷은 세션당 최신 1개이므로 재생성돼도 링크 안정(재생성 내성).
- **FCM payload**: `data.deep_link`에 full URI 문자열 탑재(예 `"runningcrew://replay/91"`). 클라는 이 값을 라우터에 위임. 알림 종류(리마인더·리플레이 열림)와 무관하게 `deep_link` 단일 필드로 라우팅(M3-C).
- 실 수신·라우팅 동작 검증은 Firebase 발급 게이트 뒤(M3-C). 규약은 지금 확정 — 서버·클라 양쪽이 동일 스킴·경로에 합의.
