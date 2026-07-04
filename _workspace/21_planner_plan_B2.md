# 21 — 기획자 작업 계획 (배치 B2: Race 컨텍스트 서버측 + 세션 화면 + CI/환경분리)

> 작성: planner · 2026-07-04 · 선행: `11_planner_plan_B.md`(§3 B2 윤곽·보존), `12_analyst_design_B.md`(B1 설계), `13/14/16` B1 보고, 커밋 779698c·adefd15
> 기준: 계획서 §3/§5/§7/§8, todolist(M1 잔여), `docs/contracts/session-api.md`(v0.1 초안), `docs/regressions.md`(R-001·R-002·R-003 CLOSED)
> 전제: M0 발급물 미확보 유지 — 카카오 앱 키·**네이버 지도 Client ID**·Firebase·도메인. 의존 작업은 §5 대기표대로 격리.

---

## 1. 요청 요약 / 범위 판정

- **요청**: B1(인증·User·Crew 수직 슬라이스, QA PASS 차단 0) 완료 위에서 B2 상세화 — todolist M1 잔여(Course+RaceSession 서버측, 세션 화면, CI, dev/prod 분리)와 M2 선행 가능분 검토.
- **범위 판정**: 전 항목 **MVP·M1**. Race 컨텍스트 **서버측**은 todolist M1 명시 항목(§Course+RaceSession)이므로 정당. 범위 외·2차 침범 0.
- **명시적 제외 (M2 게이트 — 성급히 당기지 않음)**:
  - 트래킹 실배선(AndroidForegroundTracker·TrackPoint 수집·적응형 샘플링·로컬 상태머신) — M2.
  - TrackRecord/track_payload 서버·업로드 파이프라인·정제(TrackRefinement) — M2.
  - FinishPolicy·RankingPolicy·RaceResult·세션 마감 스케줄러(전원완료/deadline → DNF/DNS) — M2.
  - 결과/순위 조회 계약·PB — M2(B2는 초안도 만들지 않음. session-api가 이미 조회 세트만 확정).
  - **클라 '레이스 시작' 트리거** — 시작=트래킹 진입이 원자적이어야 하므로 **M2로 미룸**(서버 STARTED 엔드포인트는 B2, 아래 결정 D-1 참조).
- **핵심 판단 — 지도 SDK**: §4 별도 절. 요약: **인터페이스 격리 + placeholder + 서버 시드 코스로 우회**. `flutter_naver_map` 의존성·실 어댑터·지도 그리기 UI는 **대기**. 세션 생성은 시드 코스 선택으로 성립.

---

## 2. 배치 B2 — 작업 항목 (형식: `[영역]` 작업명 — AC — 의존 — QA흡수)

### 백엔드 (Race 컨텍스트 — 현재 `race/` 패키지 골조만, 소스 0)

- **B2-S1 [backend/domain]** Course 애그리거트 + 폴리라인 서버 디코딩/거리 + 발행 후 불변 + Course API + 시드
  - AC: ① 순수 도메인 `Course`(id·crewId·name·RoutePath VO·distance_m·start/finish lat·lng·createdBy) — ArchUnit R-1 준수(Spring/JPA 무관). ② **폴리라인 서버 디코딩이 클라 `polyline_codec.dart`와 문자 단위 상호운용**: precision **1e5 고정**(클라와 동일, 1e6 아님), tie 반올림 **half-away-from-zero**(Dart `.round()` 의미론과 동형) — 골든 벡터로 박제(B2-T2). ③ 거리 계산은 디코딩 좌표 하버사인 누적(코스 총거리는 등록 시 확정·저장; 정제 후 주행거리 비교는 M2 FinishPolicy 소관). ④ **발행 불변식**: 세션에서 사용된(=race_session.course_id 참조된) Course는 수정 명령 거부 409 `COURSE_IMMUTABLE`. ⑤ API: `POST /crews/{crewId}/courses`(크루장 전용, 인코딩 폴리라인+메타 수신), `GET /crews/{crewId}/courses`(목록), `GET /courses/{courseId}`(상세). ⑥ **dev 시드 코스**: `V3__seed_dev_courses.sql` 또는 dev 프로필 시더로 크루당 더미 코스 1~2개 — **prod 미적용**(세션 생성 UI 언블록용, 지도 그리기 대기 우회). 코스 그리기 좌표 출처는 지도 SDK 확보 후.
  - 의존: 없음(Race 진입점) · domain-analyst 검토(RoutePath 불변·발행 판정·코스 생성 권한=크루장)
  - QA흡수: **폴리라인 정밀도 1e5/1e6 서버·클라 대조**(QA 3차 이월 6) — B2-T2에서 상호운용 골든 박제

- **B2-S2 [backend/domain]** RaceSession 애그리거트 + 상태머신 + 세션 CRUD + OPEN 발행 전이
  - AC: ① 순수 도메인 `RaceSession`(courseId·scheduledAt·uploadDeadline·status) + 상태머신 **DRAFT→OPEN→RUNNING→FINALIZING→COMPLETED|CANCELLED** — 각 전이 가드 TDD, 불법 전이 409 `SESSION_STATE_INVALID`. FINALIZING→COMPLETED·마감 스케줄러는 **M2**(B2는 전이 정의·수동/신호 유발 전이까지). ② `upload_deadline` NOT NULL — "scheduled+12h"는 **앱레이어 UX 기본값**(도메인 하드코딩 금지). ③ API: `POST /crews/{crewId}/sessions`(크루장 전용, DRAFT 생성), `GET /crews/{crewId}/sessions`(목록), `GET /sessions/{sessionId}`(상세·참가자 포함) — session-api v0.1 초안 shape 준수. ④ **OPEN 발행 전이**(`POST /sessions/{id}/open` 또는 생성 시 옵션 — 계약 B2-S4에서 확정): OPEN 이후 course 참조 고정(B2-S1 ④의 불변 트리거). ⑤ CLOSED 크루에서 세션 명령 전부 409(C-B3 재사용). ⑥ 탈퇴 참가자 nickname 익명 표시·행 보존(session-api 상세 규약).
  - 의존: B2-S1(course_id 참조), B2-S4(계약) · domain-analyst 검토(상태 전이표·발행 시점)
  - QA흡수: **RaceStatus 3자 대조**(QA 3차 이월) — 서버 enum↔계약↔클라 DTO(B2-C1). 클라 로컬 상태머신(READY/…) 대응은 트래킹 실배선 M2 소관(B2는 서버 RaceStatus만)

- **B2-S3 [backend/domain]** Participation: REGISTER + STARTED(멱등) + 취소 정책
  - AC: ① `Participation` enum {REGISTERED,STARTED,FINISHED,DNF,DNS,WITHDRAWN} — B2 구현 범위는 **REGISTERED·STARTED·(취소 시)WITHDRAWN·(탈퇴 시)행 보존**. FINISHED/DNF/DNS는 **M2**(업로드·마감 소관). ② `POST /sessions/{id}/register`(멤버 opt-in 신청 — DNS="신청 후 미출주" 의미론상 명시적 신청, OPEN 세션만, 중복 신청 멱등·UQ(session_id,user_id)). ③ `POST /sessions/{id}/start`(**STARTED 신호 멱등** — 이미 STARTED/FINISHED면 no-op 또는 거부, 서버 다운 허용 설계상 신호 유실 무해; 세션 최초 STARTED 시 RUNNING 전이). ④ **취소 정책** `POST /sessions/{id}/cancel`(크루장 전용, RUNNING 중에도 가능 → CANCELLED, 순위·보상 미생성). B2 시점 트랙 없음 → "뛰던 참가자 트랙 개인기록 보존"은 M2 트랙 등장 시 유효(취소 자체는 참가 상태만 정리). ⑤ 참가자 조회는 session 상세(B2-S2 ⑥)에 병합.
  - 의존: B2-S2 · domain-analyst 검토(register opt-in·STARTED 멱등·cancel 전이)
  - QA흡수: **Participation enum 3자 대조**(QA 3차 이월), 멱등·상태전이 격리

- **B2-S4 [backend/domain]** 계약 v0.3: course-api 신규 + session-api 확장(register/start/cancel/open)
  - AC: ① `docs/contracts/course-api.md` **신규** — 코스 생성/목록/상세, 폴리라인 인코딩 규약(**precision 1e5 명시** — 상호운용 진실), enum·오류코드(`COURSE_IMMUTABLE`·`FORBIDDEN`·`NOT_FOUND`)·필드 스키마. ② `session-api.md` **v0.1→v0.2** — register/start/cancel/open 명령 append(변경 이력 주석), 오류코드 `SESSION_STATE_INVALID`·enum 값 집합(RaceStatus·Participation) 재확인. ③ R-001 계약 템플릿 규칙(enum 값 집합 필수) 준수. 결과/순위·PB는 **여전히 M2**(초안 금지).
  - 의존: 없음(B2-S1~S3 병행 착수, 구현 전 확정) · domain-analyst 주도

### 클라이언트 (`app/` — B1 화면·라우팅·DTO 골격 위)

- **B2-C1 [flutter]** 세션 목록 + 세션 상세 화면 + Race DTO 소비 ("지금 뛰는 중" 표시)
  - AC: ① `lib/core/model/`에 session/course DTO(enum_codec 폴백 유틸 경유 — RaceStatus·Participation `unknown` 폴백, 계약 값 집합 대조 테스트 추가 — R-001 장치). ② 크루 상세에서 세션 목록 진입(GET sessions), 세션 상세(GET /sessions/{id}) — 코스 요약·참가자 상태·일시·업로드 마감 표시. ③ **"지금 뛰는 중" 표시** = 참가자 status STARTED 렌더(읽기 전용, 트래킹 미배선). ④ '레이스 시작' 진입점은 **M2 stub**(disabled + "트래킹 M2" 안내 — D-1). 디자인 1a 라임 토큰 준수. 위젯 테스트 ≥1/화면.
  - 의존: B2-S4(계약), B2-S2·S3(서버) · flutter-client 스킬
  - QA흡수: RaceStatus·Participation **enum 값 집합 3자 대조**(B1 `enum_contract_test` 패턴 확장)

- **B2-C2 [flutter]** 세션 생성 UI (코스 선택 = 시드 코스 목록)
  - AC: ① 크루장 세션 생성(POST sessions): 코스 선택·일시·업로드 마감. ② **코스 선택 = GET courses(시드 코스) 목록에서 선택** — 지도 그리기 없이 성립(§4). ③ **upload_deadline UX 기본값** = scheduled+12h를 앱에서 기본 제시·수정 가능(도메인 아님). ④ 보상 내용(자유 텍스트)은 **M3 Reward** 소관 — B2 미포함(세션 생성 필드에서 제외, 계획서 세션생성 UI의 "보상 내용" 라인은 M3에서 append). ⑤ 세션 목록 새로고침 반영.
  - 의존: B2-S4, B2-S1(시드 코스), B2-C1
  - QA흡수: 세션 생성 계약 3자 대조(QA 4차)

- **B2-C3 [flutter]** 지도 위젯 추상화(인터페이스 + placeholder) + 코스 미리보기
  - AC: ① **지도 위젯 인터페이스 격리**(트래킹 격리 패턴 준용 — 격리 지점 ④): `CoursePolylineMap`(폴리라인 표시용) 추상. ② **placeholder 구현**(정적 폴리라인 스케치/좌표 프리뷰 — 실 tile 없음)으로 코스 상세·선택 미리보기 렌더. ③ `flutter_naver_map` **의존성·실 어댑터·지도 그리기(경로 그리기) UI는 대기** — 어댑터 교체 지점·Client ID 주입 지점만 주석 고정(§4·§5). ④ R-002 가드: 지도 패키지가 향후 유입돼도 `lib/core` 순수성 유지(어댑터 레이어 한정 — allowlist 가드 green).
  - 의존: B2-S1(코스 폴리라인) · flutter-client 스킬
  - QA흡수: R-002 core 순수성 상시 재확인(지도 SDK 유입 대비)

- **B2-C4 [flutter]** dev/prod 환경 분리 (flavor 또는 dart-define 구조)
  - AC: ① API base URL(현재 dart-define 골격만 존재) + 카카오 앱 키·Firebase·지도 Client ID **주입 지점**을 dev/prod로 구조화(flavor 또는 `--dart-define-from-file`). ② **실제 키 값은 대기** — 주입 구조·placeholder만(운영 DB 개발 트래픽 오염 방지 골격). ③ dev 로그인·스파이크 진입은 dev에만, prod 빌드 미포함(B1 격리 유지 확인). ④ `flutter build apk --flavor`(또는 dart-define) dev/prod 양쪽 빌드 성공.
  - 의존: 없음(독립 착수) · versionCode/versionName 규칙(계획서 M1 운영항목)은 이 작업에 병합 가능

### 테스트 / 인프라

- **B2-T1 [test]** CI 구성 (GitHub Actions) + compose 앱 컨테이너 기동 + app-version 스모크 + 공유 픽스처 — **B1-T1 이월 실행**
  - AC: ① PR/푸시 시 `flutter analyze`+`flutter test`, `./gradlew build`(Testcontainers 상시 — R-003 마이그레이션 라이브 포함) 자동. ② **앱 Docker 이미지 빌드 + compose 기동 + health 200 잡**(QA 이월 P16-3 "compose 앱 컨테이너 자체 기동" 해소). ③ **app-version 라이브 스모크 잡**(P16-2 이월 — 시드 유무 200/404 클라 게이트 통과 확인). ④ **공유 픽스처 테스트 골격**(P16-1 — 서버 생성 응답 픽스처를 앱 테스트가 파싱, 서버↔클라 실바이트 교차의 CI화 첫 도입; B2 신규 계약(session/course)부터 적용). ⑤ 실패 시 머지 차단.
  - 의존: 없음(즉시 착수 — B2 첫 주 병행) · **JDK 25 베이스 이미지 pull**은 CI 러너에서 수행(로컬 비용 회피)
  - QA흡수: **compose 앱 컨테이너 기동**(P16-3), **app-version 스모크**(P16-2), **공유 픽스처 CI화**(P16-1) — 3건 일괄

- **B2-T2 [test]** 폴리라인 상호운용 골든 + Race 도메인/상태머신 골든
  - AC: ① **폴리라인 상호운용 박제**(QA 이월 6): 클라 골든 벡터(예 `_p~iF~ps|U…`)를 **서버 디코딩 테스트에 그대로 투입** → 좌표·거리 기대값 일치. tie 반올림 **half-away-from-zero** 경계 케이스 카탈로그(±0.5 LSB). 클라측도 동일 벡터 라운드트립 유지. ② RaceSession **상태머신 골든**(합법/불법 전이 전수 — DRAFT/OPEN/RUNNING/FINALIZING/COMPLETED/CANCELLED). ③ Participation register/start **멱등·전이** 유닛. ④ Course **발행 불변** 위반 케이스. 골든 픽스처는 `golden-testing` 스킬 규약 준수.
  - 의존: B2-S1(폴리라인·Course), B2-S2/S3(상태머신) · test-engineer 주도, backend-dev/flutter-dev 시그니처 이관 수신
  - QA흡수: 폴리라인 정밀도·tie 반올림(QA 이월 6) **종결 박제**

**B2 규모**: backend 4 + flutter 4 + test 2 = **10개 항목**. 발급물 의존 0(대기 지점은 항목 내 명시·§5).

**병렬성**: B2-S4(계약)·B2-T1(CI)·B2-C4(환경분리) 선행/병행 → 서버 S1→S2→S3 순차(참조 의존), 클라 C1→C2 / C3 병행(둘 다 계약·S1 후). T2는 S1·S2·S3 산출 시그니처 수신 후.

---

## 3. 마일스톤 매핑

- **B2 전 항목 → M1**. Race 컨텍스트 **서버측**(Course·RaceSession·Participation register/start/cancel)은 todolist M1 명시. 세션 상세/생성 화면·CI·dev/prod 분리도 M1 잔여.
- **M1 완료 근접**: B2 완료 시 M1 잔여 중 남는 것 = ① 실기기 스파이크 검증(사용자 대기 게이트) ② 카카오 로그인 실연동(앱 키) ③ 지도 그리기 UI·`flutter_naver_map`(Client ID) ④ 딥링크/앱링크(도메인). 전부 **발급물·사용자 게이트**이지 구현 미비 아님.
- **M2 게이트 재강조**: 트래킹 실배선(TrackRecord·업로드·정제·FinishPolicy·마감 스케줄러·클라 로컬 상태머신·'레이스 시작' 트리거)은 **실기기 스파이크 판정(사용자 대기) 통과가 성립 조건**(계획서 §8 fail-fast). B2는 스파이크와 독립이나, **M2 착수 전 스파이크 판정 필수**.

### 실기기 스파이크 게이트 표기
- B2 작업 중 스파이크 게이트에 걸리는 항목 **없음**(트래킹 미포함). 단 D-1('레이스 시작' M2 이관)·T2의 클라 로컬 상태머신 대응은 M2에서 스파이크 판정 후.

---

## 4. 지도 SDK 판단 (핵심 질의 응답)

**결정: 인터페이스 격리 + placeholder + 서버 시드 코스로 우회. `flutter_naver_map` 의존성·실 어댑터·지도 그리기 UI는 대기.**

근거·범위:
1. **실 SDK는 Client ID 없이 무의미**: `flutter_naver_map`은 초기화 시 유효 Client ID + AndroidManifest meta-data가 있어야 타일이 렌더된다. Client ID 없이는 지도에서 경로 그리기(탭 좌표 수집)·미리보기 tile 검증이 **불가·비검증**. 지금 의존성을 추가하면 빌드·매니페스트에 미완 설정이 스며들어 리스크만 든다.
2. **코스 확보를 지도와 분리**: 코스 도메인(RoutePath·폴리라인 인코딩)은 이미 순수 Dart(`polyline_codec.dart`)로 존재. B2는 **서버 dev 시드 코스**(B2-S1 ⑥) + **코스 선택 목록 UI**로 세션 생성을 언블록한다. 지도 그리기 없이 M1 세션 흐름이 성립.
3. **격리 구조는 지금 심는다**(B2-C3): 트래킹 격리 패턴(LocationTracker 등)과 동형으로 `CoursePolylineMap` 추상 위젯 + placeholder 구현. Client ID 확보 시 **네이버 어댑터 1개 + init + 그리기 UI만** 추가하면 되도록 교체 지점·주입 지점을 주석 고정. R-002 가드로 코어 순수성 보장.
4. **대기로 남기는 것**(§5): `flutter_naver_map` pubspec 의존성, 실 지도 어댑터, "지도에서 경로 그리기" 등록 UI, Client ID 주입(B2-C4의 dev/prod 구조에 자리만).

즉 세 선택지 중 **"SDK 통합 구조(인터페이스)만 + placeholder 지도 + 시드 코스 우회"** — "아예 대기"보다 진척하되(격리 골격·세션 흐름 완성), "실 SDK 스텁 렌더"는 Client ID 없이 검증 불가라 하지 않는다.

---

## 5. 대기 목록 (M0 발급물 게이트 — 변동 항목만)

| 작업 | 필요 발급물 | B2 내 대체/격리 |
|---|---|---|
| 지도 그리기 UI·`flutter_naver_map` 연동·Client ID 주입 | **네이버 지도 Client ID** | B2-C3 인터페이스+placeholder / B2-S1 서버 시드 코스 / B2-C4 주입 지점만 |
| 카카오 로그인 실연동 | 카카오 앱 키 | B1 스텁 verifier + dev 로그인 유지 |
| 초대 카톡 공유·딥링크·세션 리마인더 FCM | 도메인·Firebase | B1 코드 표시+클립보드 / FCM은 M3 |
| dev/prod 실제 키 값 | 카카오·네이버·Firebase | B2-C4 주입 구조·placeholder만 |
| 방침·약관 URL, release 서명·Crashlytics | 도메인·키스토어·Firebase | B1 placeholder 유지 / 서명·Crashlytics는 M4 직전·M2 |

---

## 6. QA·테스트 이월 항목 흡수표 (B2 소관)

| 이월 항목 (출처) | 흡수 작업 | 처리 |
|---|---|---|
| 폴리라인 정밀도 1e5/1e6 서버·클라 대조 (QA 3차 이월 6, 05 §4) | **B2-S1 + B2-T2** | 서버 1e5 고정·상호운용 골든 벡터 박제, tie half-away-from-zero |
| RaceStatus ↔ 클라 상태 대응 (QA 3차 이월) | **B2-S2 + B2-C1** | 서버 RaceStatus enum 3자 대조. 클라 **로컬** 상태머신 대응은 M2(트래킹) |
| track_payload 순위조회 격리 (QA 3차 이월) | **M2 유지** | B2에 순위·트랙 없음 — 격리는 B1에서 payload 연관 0건 확인됨, M2 순위 조회 시 재검증 |
| compose 앱 컨테이너 기동 CI (P16-3) | **B2-T1 ②** | 이미지 빌드+compose 기동+health 잡 |
| app-version 스모크 CI (P16-2) | **B2-T1 ③** | 라이브 스모크 잡(200/404 게이트) |
| 공유 픽스처(서버 실바이트↔앱 파싱) CI화 (P16-1) | **B2-T1 ④** | session/course 신규 계약부터 공유 픽스처 도입 |
| enum 계약 대조 R-001 상시 장치 | **B2-C1** | RaceStatus·Participation를 `enum_contract_test`에 추가 |
| P-2 실기기 1시간·P-4 notification 대칭 | **M2 유지** | 트래킹 실배선 소관 — B2 범위 아님 |

---

## 7. 열린 질문

**없음** (사용자 결정 필요 사항 없음).

- 아래는 **domain-analyst 설계로 해소**(사용자 에스컬레이션 불요): 세션 참가 opt-in 여부(DNS="신청 후 미출주" 의미론상 **명시적 신청** 확정), 코스 생성 권한(세션 생성이 크루장 전용이므로 **코스도 크루장** 기본), OPEN 발행 시점(생성 즉시 vs 별도 open 명령 — 계약 B2-S4에서 확정).
- **결정 D-1 (planner 판단·기록)**: 클라 '레이스 시작' 트리거는 **M2로 이관**(start=트래킹 진입이 원자적이어야 유실 위험·혼란 방지). 서버 STARTED 엔드포인트·"지금 뛰는 중" 읽기 표시는 B2 유지 — 상태머신·계약 완결성 확보 + M2가 검증된 레일 위에 올라감. 이견 시만 회신.
- **참고 (대기, 질문 아님)**: 네이버 지도 Client ID·카카오 앱 키는 M0 발급물 게이트 — §5대로 격리 진행. 확보 시 지도 그리기 UI·카카오 실연동을 후속 배치에서 어댑터 추가로 소화.
