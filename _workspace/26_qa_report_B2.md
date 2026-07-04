# 26 — QA 검증 보고 (배치 B2: Race 컨텍스트 서버측 + 세션 화면 앱↔서버 3자 대조)

> 작성: qa · 2026-07-04 · 대상: `backend/`(race B2-S1~S3) + `app/`(B2-C1~C4)
> 기준: `docs/contracts/course-api.md` v0.1 · `session-api.md` v0.2 · `conventions.md`, `22_analyst_design_B2.md`(CO/RS/PA 불변식), `23_backend_report_B2.md`, `24_flutter_report_B2.md`, `16_qa_report_B1.md`(이월), 스킬(qa-integration·domain-model)

## 판정 요약

**PASS — 차단 0건 / 경고 1건 / 참고 3건.** 경계면 불일치 0. 쟁점 2건 판정 완료. 신규 회귀 등록 없음(경고 1건은 스펙 해석 갈림 — 버그 아님, planner/analyst 재조정 대상).

| 검증 범위 | 결과 | 방식 |
|---|---|---|
| 1. 3자 대조 (course·session 필드 단위·명령 응답 shape) | 통과 (불일치 0) | 정적 필드 대조 + 테스트 재현 |
| 2. enum 3자 (RaceStatus 6·Participation 6) | 통과 | 정적 + 테스트(양쪽) |
| 3. 쟁점 판정 2건 | 완료 (a 정상 / b 경고) | 계약·설계·계획 근거 |
| 4. 불변식 (CO-B5 불변·RS 매트릭스·PA 멱등·deadline NN·payload 격리) | 통과 | 정적 + 도메인 가드 |
| 5. 실행 검증 (gradle test·flutter test) | 통과 | 실행 |
| 6. 이월 종결 (폴리라인 1e5 상호운용) | 종결 확인 | 정적(파일 존재·커버) |

---

## 이전 발견 재검증 (회귀 확인)

- **R-002 (core 순수성)** — CLEAN 재확인. 신규 `lib/core/model/race_dtos.dart`는 `../geo/*` + `enum_codec` 상대경로만 import, `package:` 0건. 폴리라인은 기존 `lib/core/geo/PolylineCodec` 재사용(신규 core 의존 없음). flutter test 112 green(`no_platform_imports_test` 포함).
- **R-003 (ddl validate)** — 유효. B2 신규 엔티티 3종(Course/RaceSession/Participation) `ddl-auto=validate`+Testcontainers 통과(backend §1). `route_polyline` LONGTEXT는 `@JdbcTypeCode(LONGVARCHAR)`로 validate 정합(backend §5).
- **B1 이월 "track_payload 격리·RaceStatus 상태머신"** — 본 회차 대상, 아래에서 종결.

---

## 1. 3자 대조 — 계약 ↔ 서버 record ↔ 클라 DTO (불일치 0건)

방식: 계약 필드표 ↔ 서버 응답 record(`@JsonNaming` SNAKE_CASE, B1 라이브 실측 확정) ↔ 클라 `fromJson` 키. Jackson enum은 `name()` 대문자 직렬화(B1 라이브 확정).

### course-api §3 CourseDetail
`CourseDetailResponse`(id, crewId, name, routePolyline, distanceM, startLat/Lng, finishLat/Lng, createdBy, createdAt) → snake_case → 계약 9필드 + `created_by`/`created_at` 전부 일치. 클라 `CourseDetail.fromJson` 동일 키. **일치.**

### course-api §2 CourseSummary (경량)
`CourseSummaryResponse`(id, crewId, name, distanceM, createdAt) → 계약 일치. 폴리라인 미포함(경량 규약 준수). 클라 `CourseSummary.fromJson` 일치. **일치.**

### session-api §3 SessionDetail — **flutter-dev 지목 쟁점 2 판정**
- 서버 `SessionDetailResponse.Course`(중첩 record) 필드 = **id, name, distanceM, routePolyline, startLat/Lng, finishLat/Lng** — 정확히 8필드. **`crew_id`/`created_by`/`created_at` 없음** → 계약 §3 course 요약(8필드)과 **문자 단위 일치**.
- 클라 `CourseDetail.fromJson`은 `crew_id`/`created_by`/`created_at`을 **nullable**로 읽음(`as int?`, null 가드) → SessionDetail.course(3필드 부재)·GET /courses/{id}(3필드 존재) **양쪽 모두 안전 파싱**. 동일 DTO 재사용이 정합적.
- **판정: 3자 정합. 불일치 없음.** flutter-dev의 우려(서버가 이 필드를 넣는지)는 해소 — 서버는 넣지 않고, 클라는 없어도 안전.
- participants 요소: 서버 `Participant`(userId, nickname, status) → `user_id`/`nickname`/`status` → 계약·클라 `ParticipantView.fromJson` 일치.

### session-api §2 SessionSummary
`SessionSummaryResponse`(id, crewId, courseId, courseName, status, scheduledAt, uploadDeadline, participantCount) → snake_case(`course_name`, `participant_count`, `upload_deadline`) → 계약·클라 `SessionSummary.fromJson` 일치. **일치.**

### 명령 응답 shape — **flutter-dev 지목 쟁점 4 판정**
`RaceSessionController`의 open/register/start/cancel **4종 전부** `SessionDetailResponse.from(...)` 반환(200) — 생성은 201 SessionDetail. **클라 가정("모든 명령 = SessionDetail §3 후 invalidate")과 정합.** 204·다른 shape 없음. 클라 `_command`가 응답 body를 `SessionDetail.fromJson`으로 파싱 → 안전. **일치.**

### 시각 직렬화 (쟁점 5)
클라 `CreateSessionRequest.toJson`: `toUtc().toIso8601String()`(밀리초 `.000Z` 포함) 송신. 서버 `CreateSessionRequest`는 `Instant` 필드(JavaTimeModule) — 밀리초·`Z` 파싱 수용(B1에서 Instant 왕복 라이브 확정). **정합**(정적 + B1 라이브 근거).

---

## 2. enum 3자 대조 (R-001 유형) — 통과

| enum | 계약 값 집합 | 서버 domain enum | 클라 wireValues | 판정 |
|---|---|---|---|---|
| RaceStatus | DRAFT/OPEN/RUNNING/FINALIZING/COMPLETED/CANCELLED | `RaceStatus.java` 6값 동일 순서 | `race_dtos.dart` 6값+unknown 폴백 | 일치 |
| ParticipationStatus | REGISTERED/STARTED/FINISHED/DNF/DNS/WITHDRAWN | `ParticipationStatus.java` 6값 동일 | 6값+unknown 폴백 | 일치 |

- 서버 응답 record가 enum을 직접 필드로 보유 → Jackson `name()` 대문자 직렬화(계약·클라 wire 대문자와 일치, B1 라이브 확정 패턴).
- 미구현값(FINALIZING/COMPLETED/FINISHED/DNF/DNS)도 클라가 **정식 멤버로 파싱**(unknown 아님) — 집합 보존. `enum_contract_test.dart`(R-001 상시 장치)가 6값 집합 이탈을 즉시 red 처리. flutter test green.
- 송신 enum 폴백 금지 유효 — B2 클라→서버 enum 송신 경로 0건(register/start/open/cancel 모두 body 없음).

---

## 3. 쟁점 판정 2건 (판정 권한 위임)

### 쟁점 a — 참가 self-cancel(unregister) 부재 → **B2 범위 밖 정상 (참고, 차단/경고 아님)**
근거:
- **도메인 모델**: Participation 상태집합에 "등록 취소" 상태·전이가 없다. 신청(REGISTERED) 후 미출주는 마감 시 **DNS로 판정**(M2)하는 것이 설계된 메커니즘 — 명시적 unregister가 없어야 DNS 의미론이 성립(설계 22 §3.2 J-1).
- **계약**: session-api v0.2는 register/start/cancel(크루장 세션 취소)만 정의. self-unregister는 정의·부재 모두 의도적.
- **클라 처리**: `session_detail_screen.dart:276` — "참가 취소(unregister)는 계약 미제공 → 미노출"로 명시, 임의 엔드포인트 발명 안 함(원칙 1 준수). 
- **판정**: 계약 갭 아님. 제품이 명시적 참가 철회를 원하면 향후 계약 확장 사안(domain-analyst)이나, MVP/B2 범위에서는 DNS 경로가 정상 대체물. **참고 P26-1로 기록**(제품 결정 대기 항목).

### 쟁점 b — D-1 '레이스 시작' 버튼 활성 (트래킹 없는 STARTED→RUNNING) → **M1 수용 가능하나 설계 갈림 재조정 필요 (경고)**
근거:
- **다운스트림 무해**: M1엔 트랙 업로드·FinishPolicy·마감 스케줄러·FINALIZING/COMPLETED·순위·보상이 전부 부재. STARTED→RUNNING 전이는 상태 라벨만 바꾸며, RUNNING에서 cancel로 탈출 가능(매트릭스 합법). "phantom RUNNING"이 유발하는 데이터 손상·잘못된 순위·보상 생성 경로 없음.
- **계약 정합**: session-api §6 start는 **라이브 엔드포인트**로 정의됨("최초 STARTED가 OPEN→RUNNING", "클라 트래킹 실배선은 M2"). 서버는 계약대로 구현, 클라가 이를 호출하는 것은 **경계면 버그가 아님**(qa-integration 상시점검의 "서버가 모르는 상태 전송" 안티패턴에 해당하지 않음 — STARTED는 정식 서버 상태).
- **갈림의 실체**: analyst 설계 22 §4.3은 "'레이스 시작' 진입점 = M2 stub(disabled + 트래킹 M2 안내)"로 기술. flutter-dev는 태스크 문구("start 신호 API 호출 UI까지만")에 따라 **활성 버튼 + M2 안내 문구**(`_RaceStartEntry`, session_detail_screen.dart:381)로 구현. 두 스펙이 갈린다.
- **판정**: 기능·경계면상 M1 수용 가능(차단 아님). 단 **analyst 설계(disabled stub)와 구현(활성 신호)의 명시적 불일치**가 남아있어, 어느 쪽이 정본인지 planner/domain-analyst가 확정해야 한다 — 확정 없이 두면 M2 트래킹 배선 시 "신호는 이미 갔는데 트래킹은 안 켜진" 반쪽 상태를 유저가 만들 수 있다(현 안내 문구로 완화되나 근본 해소는 스펙 통일). **경고 W26-1로 등록, planner/analyst 통지.**
  - 축소 경로(설계 정본이면): `_RaceStartEntry`를 비활성 안내로 1곳 축소(flutter-dev 보고 §5 명시) — 저비용.

---

## 4. 불변식 검증 — 통과

| 불변식 | 확인 | 방식 |
|---|---|---|
| CO-B5 코스 발행 후 불변 (수정/삭제 API 부재) | `CourseController` = POST/GET/GET만. `grep PutMapping\|DeleteMapping\|PatchMapping` race 패키지 **0건**. FK RESTRICT(V1) 구조 방어. | 정적(grep) |
| CO-B3 distance_m 서버 확정 | 요청 record에 distanceM 없음(CreateCourseRequest 미수신) → 서버 폴리라인 계산. | 정적 |
| RS-B1 상태머신 매트릭스 | `RaceSessionPolicy.apply` 가드 + `RaceSession.open/cancel/onStartSignal/ensureRegisterable` → 불법전이 `IllegalSessionTransitionException` → 409 `SESSION_STATE_INVALID`(`applyTransition`). backend seed `RaceSessionPolicyTest`(gradle green). | 정적 + 테스트 |
| RS-B4 upload_deadline NOT NULL 且 > scheduled_at | `RaceSession.create` 가드(null·역전 → `InvalidRaceSessionException` → 400). `@NotNull` DTO. | 정적 |
| RS-B2/B3 크루장 전용·CREW_CLOSED | `requireLeaderOnActiveCrew`(create/open/cancel) → 비크루장 403, CLOSED 409. | 정적 |
| PA-B1 register 멱등·OPEN·ACTIVE 멤버 | `register`: `isActiveMember` 아니면 403, `ensureRegisterable`(OPEN 아니면 409), 기존 존재 시 save 생략(멱등). | 정적 |
| PA-B2 start 멱등·선 register | `start`: participation 부재 → 409, `Participation.start()` STARTED/FINISHED no-op. | 정적 |
| PA-B3 최초 start OPEN→RUNNING | `session.onStartSignal()` → Policy START. | 정적 |
| **track_payload 격리** | `SessionQueryAdapter.findParticipants` = `participation JOIN user`만. payload 테이블/연관 **0건 참조**. B2에 트랙·순위 조회 경로 없음. | 정적(코드 전수) |
| RS-B7/PA-B6 탈퇴 익명 보존 | 참가자 nickname = `user.nickname` 네이티브 조인(탈퇴 시 이미 "탈퇴한 러너" 익명화, 행 보존). user 도메인 클래스 미참조(R-2 경계). | 정적 |
| replay_notified_at | `RaceSession.replayNotifiedAt` 항상 null(M2). FCM 발송 경로 B2 부재. | 정적 |

---

## 5. 실행 검증 (조용한 생략 없음)

| 단계 | 수행 | 결과 |
|---|---|---|
| `./gradlew test` | 실행 | **BUILD SUCCESSFUL** (60 tests — ArchUnit R1~R4·PolylineCodecInteropTest·RaceSessionPolicyTest·RaceSessionHttpFlowTest·R-003 마이그레이션 라이브 포함) |
| `flutter test` | 실행 | **112 passed** (race DTO 6파일·enum_contract·core 순수성 가드 포함) |
| 라이브 곡선(compose+bootRun+curl) | **미재수행** | backend §1이 이미 왕복 수행·계약 일치·`down -v` 완료 보고(create→open→register→start→cancel·409 매트릭스·400 deadline). 본 회차는 재현 테스트 스위트(HttpFlowTest = MockMvc+Testcontainers MySQL 왕복)로 동등 커버 — **정적+테스트 검증, 신규 라이브 curl은 생략(중복 회피)**. |

**라이브 미재현 명시**: 세션 명령 실 curl 왕복은 본 QA 세션에서 재실행하지 않음. 근거 = (1) backend-dev가 동일 곡선을 라이브 왕복 완료(§1), (2) `RaceSessionHttpFlowTest`가 Testcontainers MySQL 위에서 HTTP 왕복을 상시 재현. B1에서 auth/crew 라이브 바이트↔클라 파싱 교차는 이미 닫힘. **잔여 리스크: "서버 실 바이트 ↔ 클라 race DTO 파싱" 교차는 CI 부재(P16-1 연장)** — 아래 참고.

---

## 6. 이월 종결 확인

- **폴리라인 1e5 상호운용(QA 3차 이월 6 / CO-B1·B2)** — **종결.** backend `PolylineCodecInteropTest.java` 존재(클라 골든 벡터 `_p~iF~ps|U…` 서버 투입), 클라 `polyline_codec_test.dart` 라운드트립 유지. 양쪽 test green. precision 1e5 문자 단위 일치·tie half-away-from-zero(`Math.round` 금지) 설계 반영 확인. test-engineer T2와 중복 실행 회피 — **결과물 존재·커버 확인만 수행.**
- **track_payload 격리·RaceStatus 상태머신(B1→B2 이월)** — 종결(§4·§2).

---

## 참고 (P — 차단/경고 아님)

- **P26-1 (참가 self-cancel 미제공)**: 쟁점 a — B2 범위 밖 정상. 제품이 명시적 참가 철회를 원할 경우에만 domain-analyst 계약 확장. 현재 DNS 경로가 대체물. 후속 제품 결정 대기.
- **P26-2 (race DTO 교차 CI 부재, P16-1 연장)**: 서버 실 응답 바이트 ↔ 클라 race DTO 파싱 교차는 QA 라이브 런에서만 닫힘(CI엔 서버 jsonPath·클라 enum_contract 독립 앵커만). MVP 적정. 후속 권고: 서버 생성 응답 픽스처를 앱 테스트가 파싱하는 공유 픽스처(B2-T1 ④).
- **P26-3 (지도·카카오·prod URL 대기)**: 네이버 Client ID·카카오 앱키·prod 도메인 대기로 placeholder — 자동검증 밖(flutter §5). 비차단 이월 유지.

---

## 7. 팀 통신

- **flutter-dev**: 쟁점 2·4 해소(3자 정합 — 서버 course 요약은 3필드 미포함, 명령 4종 전부 SessionDetail). **W26-1(D-1 레이스 시작 버튼)**: 기능은 M1 수용 가능하나 analyst 설계(disabled stub)와 갈림 — planner/analyst 정본 확정 대기. 확정이 "disabled stub"이면 `_RaceStartEntry` 축소(귀하 §5 경로).
- **domain-analyst / planner**: **W26-1 재조정 요청** — session-api §6(라이브 start)과 설계 22 §4.3(클라 disabled stub)이 갈린다. 계약은 서버 라이브를 정의하므로, "클라가 M1에서 이 버튼을 활성화하는가"를 명시 확정 요망. 쟁점 a(P26-1)는 참고.
- **test-engineer**: 폴리라인 상호운용 골든 종결 확인(중복 실행 안 함). enum_contract(앱)·RaceSessionPolicyTest(서버) R-001/상태머신 상시 장치로 유효.
- **오케스트레이터**: **차단 0 / 경고 1(W26-1, 스펙 갈림 — 버그 아님, regressions.md 미등록)**. 신규 회귀 없음. B2 경계면 무결.

## 8. 경고·참고 요약

| 등급 | ID | 내용 | 대상 |
|---|---|---|---|
| 경고 | W26-1 | D-1 '레이스 시작' 버튼: 구현(활성 신호) vs 설계(disabled stub) 갈림. M1 기능 수용 가능하나 정본 확정 필요 | planner/domain-analyst |
| 참고 | P26-1 | 참가 self-cancel 미제공 — B2 범위 밖 정상(DNS 대체) | domain-analyst(제품 결정 시) |
| 참고 | P26-2 | race DTO 서버↔클라 교차 CI 부재(P16-1 연장) | 후속 공유 픽스처 |
| 참고 | P26-3 | 지도·카카오·prod URL 대기 placeholder | 발급물 게이트 |
