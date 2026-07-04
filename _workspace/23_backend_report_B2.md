# 23 — 백엔드 구현 보고 (배치 B2: Race 컨텍스트 서버측)

> 작성: backend-dev · 2026-07-04 · 기준: `21_planner_plan_B2.md`, `22_analyst_design_B2.md`, `docs/contracts/{course,session,conventions}.md`
> 선행: B1(`779698c`) crew/user 패턴 준수. 빌드: `./gradlew build` ✅ (60 tests, ArchUnit R1~R4 포함). 라이브 곡선(compose mysql + bootJar) 왕복 curl 계약 일치 ✅ → `down -v` 완료.

---

## 1. 구현 결과 요약

| 항목 | 상태 |
|---|---|
| B2-S1 Course(불변 애그리거트·폴리라인 1e5·서버 거리 확정·크루장 생성·course-api v0.1) | ✅ |
| B2-S2 RaceSession 상태머신(DRAFT→OPEN→RUNNING / →CANCELLED)·세션 CRUD·open | ✅ |
| B2-S3 Participation register/start(멱등)·cancel 정책 | ✅ |
| dev 시드 코스(@Profile{local,dev} 시더 빈, Flyway 미사용) | ✅ |
| 순수 함수 이관 명시(폴리라인·거리·상태전이) | ✅ (§3) |

**빌드**: `BUILD SUCCESSFUL` — 60 tests 전부 통과. Testcontainers(MySQL 8) 위 `ddl-auto=validate` 라이브 검증 포함(신규 엔티티 3종 매핑 검증).
**라이브 곡선**: 스텁 로그인 → 크루 → 코스 생성(distance_m 788906 서버확정, 폴리라인 문자 단위 에코) → 세션(DRAFT) → open(OPEN) → register(REGISTERED) → start(RUNNING) → cancel(CANCELLED) 왕복. 매트릭스 위반(DRAFT register / 종료후 open) 409 `SESSION_STATE_INVALID`, deadline≤scheduled 400 확인. 시더 재기동 시 크루 존재하면 코스 2개(4805m/1850m) 생성·멱등 확인.

---

## 2. 구현 API 표 (계약 대비)

**course-api.md v0.1** (전부 `auth: required`)

| 메서드 | 경로 | 권한 | 비고 |
|---|---|---|---|
| POST | `/api/v1/crews/{crewId}/courses` | 크루장 | distance_m 서버확정, 폴리라인 ≥2점 검증. 201 CourseDetail |
| GET | `/api/v1/crews/{crewId}/courses` | ACTIVE 멤버 | 페이지 래퍼, 폴리라인 미포함 경량 |
| GET | `/api/v1/courses/{courseId}` | ACTIVE 멤버 | CourseDetail(폴리라인 포함) |

**session-api.md v0.2** (전부 `auth: required`)

| 메서드 | 경로 | 권한 | 전이 |
|---|---|---|---|
| POST | `/api/v1/crews/{crewId}/sessions` | 크루장 | →DRAFT. course 같은 크루 소유·`deadline>scheduled` 검증 |
| GET | `/api/v1/crews/{crewId}/sessions` | ACTIVE 멤버 | 목록(course_name·participant_count) |
| GET | `/api/v1/sessions/{sessionId}` | ACTIVE 멤버 | 상세(course 요약+participants, 탈퇴 nickname 익명 조인) |
| POST | `/api/v1/sessions/{sessionId}/open` | 크루장 | DRAFT→OPEN |
| POST | `/api/v1/sessions/{sessionId}/register` | ACTIVE 멤버 본인 | OPEN만, 멱등 |
| POST | `/api/v1/sessions/{sessionId}/start` | 참가자 본인 | 선 register 필요, 멱등, 첫 STARTED→RUNNING |
| POST | `/api/v1/sessions/{sessionId}/cancel` | 크루장 | DRAFT\|OPEN\|RUNNING→CANCELLED |

오류코드 매핑: `VALIDATION_ERROR`(400) / `FORBIDDEN`(403) / `NOT_FOUND`(404) / `CREW_CLOSED`(409, 크루장 명령만) / `SESSION_STATE_INVALID`(409). `COURSE_IMMUTABLE`는 예약(수정/삭제 API 부재 + FK RESTRICT로 구조 방어 — 트리거 경로 없음).

**계약 대비 편차 0.** J-1(명시적 register·OPEN·ACTIVE 멤버·멱등)/J-2(불변 애그리거트, 수정/삭제 미노출)/J-3(별도 open 명령) 설계대로 구현.

---

## 3. test-engineer 이관 — 순수 함수(골든 대상, B2-T2)

전부 `com.runningcrew.race.domain` (프레임워크 무관, ArchUnit R-1 green). backend가 seed 테스트로 상호운용·매트릭스를 이미 박제했고(아래 파일), 경계 카탈로그 확장이 test-engineer 소관.

1. **`PolylineCodec.decode(String) → List<LatLng>`** / **`encode(List<LatLng>) → String`**
   - precision **1e5**, tie **half-away-from-zero**(encode 부호분리 — `Math.round()` 금지). decode는 정수누적+나눗셈이라 반올림 무개입.
   - 예: `encode([(38.5,-120.2),(40.7,-120.95),(43.252,-126.453)]) == "_p~iF~ps|U_ulLnnqC_mqNvxq` + "`" + `@"` (클라 골든 벡터와 동일). `encode([(0,0)])=="??"`, `encode([(0,-179.9832104)])=="?` + "`" + `~oia@"`.
   - 손상 입력(위/경도 쌍 미완결)은 `InvalidCourseException`.
   - 경계 이관 요청: ±0.5 LSB tie(특히 음위도/음경도), 180 경계, 다점 왕복.

2. **`GeoDistance.totalMeters(List<LatLng>) → int`** — 연속 구간 하버사인 누적, `R=6_371_000.0`m, `Math.round`. 2점 미만 0. (코스 총거리 기준값 — 완주판정 M2와 무관.)

3. **`RaceSessionPolicy.apply(RaceStatus, SessionCommand) → RaceStatus`** — 설계 §2.4 매트릭스 전수. open: DRAFT→OPEN. register: OPEN→OPEN(불변). start: OPEN|RUNNING→RUNNING. cancel: DRAFT|OPEN|RUNNING→CANCELLED. 그 외 `IllegalSessionTransitionException`.

4. 보조: **`RaceSession.create(...)`** — `upload_deadline>scheduled_at` 위반 시 `InvalidRaceSessionException`. **`Participation.start() → boolean`** — REGISTERED→STARTED(true), STARTED/FINISHED no-op(false), 그 외 예외.

backend seed 테스트(참고·회귀 방지): `src/test/java/com/runningcrew/race/domain/PolylineCodecInteropTest.java`, `RaceSessionPolicyTest.java`.

---

## 4. qa 경계면 (3자 대조 포인트)

- **enum 3자 대조**: 서버 `RaceStatus`(6값)·`ParticipationStatus`(6값 — FINISHED/DNF/DNS는 M2 미전이지만 enum·계약에 존재) ↔ session-api §Enum ↔ 클라 DTO(B2-C1). `@Enumerated(STRING)` 고정.
- **폴리라인 1e5 상호운용**(CO-B1/B2): 서버 decode/encode = 클라 `polyline_codec.dart` 문자 단위. 라이브 곡선에서 create→detail 폴리라인 문자열 무변경 에코 확인.
- **상태머신 매트릭스**(RS-B1): §2 표 · 라이브 curl로 합법/불법 전이 왕복 확인.
- **track_payload 격리**: B2에 트랙·순위 경로 없음(payload 연관 0건 유지). M2 재검증 대상.
- **dev 시드 prod 미유출**(CO-B7): 시더 `@Profile({local,dev})` — prod 프로필엔 빈 부재로 부팅 시 미실행(구조 격리). Flyway 미사용.
- **컨텍스트 경계**(R-2): race는 crew/user 클래스 미참조 — 크루 접근(`CrewAccessPort`)·참가자 nickname은 crew/crew_member/user 테이블 **네이티브 SQL 조인**으로만 해석.

---

## 5. 설계·구현 판단 노트 (편차 없음, 명시만)

- **CREW_CLOSED 적용 범위**: 계약상 create/open/cancel(크루장 명령)만 `CREW_CLOSED`(409) 반환. register/start는 계약에 CREW_CLOSED 미기재 — CLOSED 크루엔 ACTIVE 멤버가 없어 멤버십 검사에서 403으로 귀결(계약 §5·§6 오류표와 일치). RS-B3의 "세션 명령 전부"는 크루장 관리 명령으로 해석.
- **start 403 vs 409 분기**: ACTIVE 멤버 아님 → 403 `FORBIDDEN`(권한). 멤버지만 participation 부재 → 409 `SESSION_STATE_INVALID`(선 register). 계약 §6 오류표대로.
- **LONGTEXT 매핑**: `route_polyline`(LONGTEXT)은 `@Lob`이 CLOB(tinytext)로 잡혀 `validate` 갈림 → `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`로 고정(어댑터 엔티티 한정, 도메인 무관). Testcontainers validate로 검증됨.

---

## 6. 미규정·보류

- **미규정-1(dev 시드 대상 크루)** — backend 재량 확정: **가장 먼저 생성된 ACTIVE 크루**의 크루장 명의로 더미 코스 2개(한강 반포-잠실 5K / 남산 순환 3K, 서울 근사 좌표). 크루 없으면 no-op. prod 미유출만 불변(CO-B7).
- **M2 명시적 제외(구현 안 함)**: FINISHED/DNF/DNS 전이·마감 스케줄러·FINALIZING→COMPLETED·트랙 업로드·순위·리플레이. `replay_notified_at`은 컬럼만(NULL 유지).
- **보류 없음** — 계약 모호·발급물 게이트로 막힌 항목 0. course-api v0.1 / session-api v0.2 전량 구현.

---

## 7. 신규 파일 (backend/)

- 도메인(`race/domain`): LatLng, PolylineCodec, GeoDistance, RoutePath, Course, RaceStatus, SessionCommand, RaceSessionPolicy, RaceSession, ParticipationStatus, Participation, InvalidCourseException, InvalidRaceSessionException, IllegalSessionTransitionException
- 애플리케이션(`race/application`): CourseCommandService, CourseQueryService, RaceSessionCommandService, RaceSessionQueryService, DevCourseSeeder, CreateCourseCommand, CreateSessionCommand, view/{CourseSummary,CourseDetail,SessionSummary,SessionDetail}View, port/out/{CourseRepository,CourseQueryPort,RaceSessionRepository,ParticipationRepository,SessionQueryPort,CrewAccessPort,CourseSeedPort}
- 어댑터(`race/adapter`): in/web/{CourseController,RaceSessionController}+dto 6종, out/persistence/{Course,RaceSession,Participation}{JpaEntity,JpaRepository,PersistenceAdapter}, CourseQueryAdapter, SessionQueryAdapter, CrewAccessAdapter, CourseSeedAdapter
- 테스트: race/domain/{PolylineCodecInteropTest,RaceSessionPolicyTest}, integration/RaceSessionHttpFlowTest
