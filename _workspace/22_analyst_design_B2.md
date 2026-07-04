# 22 — 도메인 설계·계약 확정안 (배치 B2: Race 컨텍스트 서버측 — Course·RaceSession·Participation)

> 작성: domain-analyst · 2026-07-04 · 기준: `21_planner_plan_B2.md`, `12_analyst_design_B.md`(B1 — 규약 유효), `16_qa_report_B1.md`(이월), `domain-model` 스킬 Race 컨텍스트, 계획서 §5.2, `docs/contracts/`(session v0.1·conventions v0.1.1), `V1__init.sql`(course·race_session·participation DDL 확정)
> B1 설계와 충돌 없음 — 본 문서는 Race 컨텍스트 델타. session-api 조회 세트(v0.1)는 shape 유지·명령만 append.

---

## 0. planner 판단 3건 — domain-analyst 확정

| # | 질문 | 확정 | 계획서 근거 |
|---|---|---|---|
| **J-1** | 세션 참가 opt-in 방식 | **명시적 신청** `POST /sessions/{id}/register`. **OPEN 상태에서만**, 크루 **ACTIVE 멤버**만, 멱등(중복 no-op). 미신청자는 명단 부재 = 자동 REGISTERED 없음 | `domain-model` Participation `DNS`="신청 후 미출주" → 신청은 명시적 행위여야 DNS가 성립. Crew 불변식(멤버만) |
| **J-2** | 코스 생성 권한 | **크루장 전용**. 나아가 **Course는 불변 애그리거트 — 생성·조회만, 수정·삭제 명령 미노출**(발행 후 불변을 구조적으로 보장) | 계획서 §5.2 "수정 필요 시 새 코스 생성". 세션 생성이 크루장 전용이므로 그 재료인 코스도 크루장 |
| **J-3** | OPEN 발행 시점 | **별도 명령** `POST /sessions/{id}/open`(DRAFT→OPEN). 생성 즉시는 DRAFT. OPEN 전이가 곧 **코스 발행(불변 잠금)·참가 개방 시점** | DRAFT가 상태머신에 존재하는 이유 = 발행 전 준비 상태. register가 OPEN 한정(J-1)이므로 DRAFT는 크루장 준비 단계여야 의미가 있음 |

**J-2 상향 해석 고지(planner AC B2-S1 ④ 관련)**: planner는 "세션에서 사용된 Course는 수정 명령 거부 409 `COURSE_IMMUTABLE`"로 기술. 나는 이를 **완화가 아닌 강화**로 재정의한다 — 코스에 수정/삭제 API 자체를 노출하지 않으면 "발행 후 불변"이 런타임 게이트가 아니라 **구조로** 보장된다(계획서 규범 "새 코스 생성"과 정합). `COURSE_IMMUTABLE`는 계약에 **정의만 유지**(향후 코스 삭제/관리 API 도입 시 OPEN 이상 세션 참조 코스를 거부하는 의미론의 예약 코드). B2 필수 강제는 "수정/삭제 API 부재 + FK RESTRICT". 이견 시 회신.

---

## 1. Course 애그리거트 (B2-S1)

### 1.1 구성 (순수 도메인 `race/domain` — ArchUnit R-1: Spring/JPA 무관)

- **Course** (애그리거트 루트): `id`, `crewId`, `name`, `routePath`(RoutePath VO), `distanceM`, `startPoint`/`finishPoint`(LatLng VO), `createdBy`(userId), `createdAt`.
- **RoutePath** (VO): 인코딩 폴리라인 문자열 + 디코딩 좌표열. **encode/decode는 순수 함수** — 골든 대상(B2-T2). IO·시계·랜덤 금지.
- **LatLng** (VO): `lat`, `lng` (double).
- 이벤트: 없음(B2). Course는 생성 후 불변이라 상태 이벤트 부재.

### 1.2 폴리라인 인코딩 — precision 1e5 고정 (상호운용 진실)

| 항목 | 값 | 근거 |
|---|---|---|
| 알고리즘 | Google Encoded Polyline (zigzag, 5비트 청크) | 표준. 클라 `polyline_codec.dart` 이미 구현 |
| **precision** | **1e5 (×100000)** — **1e6 아님** | 클라 `PolylineCodec._precision = 100000`과 **문자 단위** 상호운용. 서버가 1e6 쓰면 좌표 10배 어긋남 |
| tie 반올림 | **half-away-from-zero** (`(coord * 1e5)` 반올림 시 정확히 .5는 절댓값 큰 쪽) | Dart `num.round()`와 동형. **서버는 `Math.round()`(half-up, 음수에서 상이) 금지** — 음위도/음경도에서 갈림. `BigDecimal RoundingMode.HALF_UP`(부호분리) 또는 수동 구현 |
| 거리 | 디코딩 좌표 **하버사인 누적** → `distance_m`(INT)로 **등록 시 서버 확정·저장** | 클라 제출값 불신. 정제 후 주행거리 비교는 M2 FinishPolicy 소관(코스 총거리는 안티치트 아닌 완주 기준값) |

- **B2-T2 박제 의무**: 클라 골든 벡터(예 `_p~iF~ps|U…`)를 **서버 디코딩 테스트에 그대로 투입** → 좌표·거리 기대 일치. tie 경계(±0.5 LSB) 카탈로그.

### 1.3 발행 후 불변 — 구현 방식 (J-2)

- **1차 방어(구조)**: 수정·삭제 엔드포인트 미노출. Course는 생성 후 절대 변경 없음.
- **2차 방어(참조 무결성)**: `race_session.course_id` FK **ON DELETE RESTRICT**(V1 확정) — 어떤 세션이 참조 중이면 물리 삭제 불가.
- **의미론 게이트(예약)**: 향후 코스 삭제/관리 API 도입 시 `SELECT COUNT(*) FROM race_session WHERE course_id=? AND status <> 'DRAFT'` > 0 → 409 `COURSE_IMMUTABLE`. (OPEN 이상 = 발행됨. DRAFT-only 참조는 미발행이나, B2는 삭제 API 부재로 무관.)

### 1.4 dev 시드 코스 (V3 마이그레이션 — dev 프로필 한정)

- **파일**: `V3__seed_dev_courses.sql` **또는** dev 프로필 시더 빈(`@Profile({"local","dev"})`). **prod 미적용**(운영 트래픽 오염 방지 — B2-C4 환경분리와 정합).
- 마이그레이션으로 넣을 경우 prod에도 실행되므로 **`@Profile` 시더 빈 권장**(Flyway는 프로필 분기 불가). 시더가 dev 부팅 시 크루가 없으면 no-op, 있으면 크루당 더미 코스 1~2개 upsert(멱등 — 재기동 중복 금지, name UNIQUE 없으니 존재 검사).
- 좌표 출처: 지도 SDK 확보 전이므로 **하드코딩 폴리라인**(한강 5K 등 실제 근사 좌표열, 1e5 인코딩). 세션 생성 UI(B2-C2) 언블록용. `distance_m`은 시더도 서버 계산 경로 통과 또는 값 명시.
- **미규정 — 제안**: 시드 대상 크루 선정 = dev에서 생성된 첫 크루(또는 시더가 데모 크루 자체 생성). backend-dev 재량, prod 미유출만 불변.

### 1.5 Course 불변식 체크리스트 (qa용)

| # | 불변식 | 강제 수단 |
|---|---|---|
| CO-B1 | 폴리라인 precision **1e5** — 서버 디코딩 = 클라 인코딩 문자 단위 일치 | B2-T2 상호운용 골든(클라 벡터 서버 투입) |
| CO-B2 | tie 반올림 half-away-from-zero (음좌표 포함) | B2-T2 경계 카탈로그 |
| CO-B3 | `distance_m`은 서버가 폴리라인에서 계산·확정(클라 제출값 불신) | 코드 — 생성 유스케이스 |
| CO-B4 | Course 생성은 **크루장 전용**, 같은 크루 소유만 | 코드 — 권한 체크 |
| CO-B5 | 발행 후 불변: 수정/삭제 API 부재 + FK RESTRICT | 구조(엔드포인트 부재) + 스키마 |
| CO-B6 | 도메인 클래스에 Spring/JPA import 0 | ArchUnit R-1 |
| CO-B7 | dev 시드 코스 prod 미유출 | `@Profile` 격리 + prod 부팅 시 코스 0 확인 |

---

## 2. RaceSession 애그리거트 + 상태머신 (B2-S2)

### 2.1 구성 (순수 도메인 `race/domain`)

- **RaceSession** (루트): `id`, `crewId`, `courseId`, `scheduledAt`, `uploadDeadline`, `status`(RaceStatus), `replayNotifiedAt`(M2·현재 NULL 유지). 참가자 컬렉션은 조회 모델에서 조인(§4.3와 동일 패턴).
- 순수 도메인 — 상태 전이 메서드가 가드 포함, 불법 전이 시 도메인 예외 → 409 `SESSION_STATE_INVALID`.

### 2.2 상태머신 — RaceStatus (B2 구현 범위 표시)

```
        POST /open (크루장)            첫 STARTED 신호            [M2: 전원완료|deadline]   [M2]
DRAFT ───────────────────▶ OPEN ───────────────────▶ RUNNING ──────────────▶ FINALIZING ────▶ COMPLETED
  │                          │                          │
  └──────────┬───────────────┴──────────────┬───────────┘
             ▼ POST /cancel (크루장)          ▼
          CANCELLED  ◀───────────────────────┘
```

| 전이 | 트리거 | 권한 | 가드·불변식 | B2 |
|---|---|---|---|---|
| (생성) → DRAFT | `POST /crews/{id}/sessions` | 크루장 | 크루 ACTIVE, course 같은 크루 소유, `upload_deadline > scheduled_at` | ✅ |
| DRAFT → OPEN | `POST /sessions/{id}/open` | 크루장 | 현 status=DRAFT. 이후 course 참조 고정(발행) | ✅ |
| OPEN → RUNNING | **첫 STARTED 신호**(`POST /sessions/{id}/start` 최초 수신) | 참가자(자동) | 현 status=OPEN. 신호 유실 무해(서버 다운 허용) | ✅ |
| RUNNING → FINALIZING | 전원 업로드 완료 | 시스템 | — | ❌ M2 |
| OPEN\|RUNNING → FINALIZING | upload_deadline 경과(스케줄러) | 시스템 | STARTED 유실 시 RUNNING 건너뜀 허용은 M2 확정(§8 미규정-2) | ❌ M2 |
| FINALIZING → COMPLETED | 결과 확정 | 시스템 | 순위·보상 생성 | ❌ M2 |
| DRAFT\|OPEN\|RUNNING → CANCELLED | `POST /sessions/{id}/cancel` | 크루장 | RUNNING 중에도 가능. 순위·보상 미생성 | ✅ |

- **종료 상태**: COMPLETED·CANCELLED. 여기서 모든 전이 명령 409 `SESSION_STATE_INVALID`.
- **불법 전이 전수**: 예) DRAFT에서 register/start/cancel? cancel만 허용. start는 OPEN/RUNNING만. 아래 §2.4 표.

### 2.3 발행(OPEN)·RUNNING 진입 시점 확정 (J-3 + planner 질의)

- **OPEN 발행**: 생성 즉시 아님 → **별도 open 명령**. OPEN 시 course_id 참조가 발행으로 잠김(J-2 게이트 대상).
- **RUNNING 진입**: **첫 STARTED 신호**(scheduled_at 자동 전이 아님). 스케줄러 기반 자동 전이는 M2. STARTED는 보조 신호이므로 유실돼도 세션은 OPEN에 남고 M2 업로드/마감이 성립해야 함(§8 미규정-2).

### 2.4 합법/불법 명령 매트릭스 (B2-T2 상태머신 골든 전수 대상)

| 명령 \ status | DRAFT | OPEN | RUNNING | FINALIZING | COMPLETED | CANCELLED |
|---|---|---|---|---|---|---|
| open | ✅→OPEN | 409 | 409 | 409 | 409 | 409 |
| register | 409¹ | ✅ | 409² | 409 | 409 | 409 |
| start | 409 | ✅(첫→RUNNING) | ✅(멱등) | 409 | 409 | 409 |
| cancel | ✅→CANCELLED | ✅→CANCELLED | ✅→CANCELLED | 409³ | 409 | 409 |

- ¹ DRAFT는 미발행 → 참가 불가. ² RUNNING 후 신규 register 차단(시작된 레이스 명단 추가 혼란 방지 — §8 미규정-3). ³ FINALIZING 취소는 M2 결정.
- 모든 409 = `SESSION_STATE_INVALID`.

### 2.5 upload_deadline

- **NOT NULL**(V1·계약 확정). **도메인 검증**: `upload_deadline > scheduled_at` (400 `VALIDATION_ERROR`) — 마감이 예정보다 앞설 수 없음(도메인 무결성, 강제).
- **"예정 +12h"는 앱레이어 UX 기본값** — 도메인 하드코딩 금지(계획서 명시). 앱이 기본 제시·수정 가능(B2-C2 ③).

### 2.6 RaceSession 불변식 체크리스트 (qa용)

| # | 불변식 | 강제 수단 |
|---|---|---|
| RS-B1 | 상태 전이는 §2.4 매트릭스만 — 불법 전이 409 `SESSION_STATE_INVALID` | 코드 — 도메인 가드 TDD(전이 전수) |
| RS-B2 | 세션 생성·open·cancel은 **크루장 전용** | 코드 — 권한 체크 |
| RS-B3 | CLOSED 크루에서 세션 명령 전부 409 `CREW_CLOSED`(C-B3 재사용) | 코드 |
| RS-B4 | `upload_deadline` NOT NULL 且 > `scheduled_at` | 스키마 NN + 코드 검증 |
| RS-B5 | course_id는 같은 crew 소유 코스만 | 코드 — 생성 시 검증 |
| RS-B6 | OPEN 이후 course 참조 발행 잠금(J-2 게이트) | 코드(향후 삭제 API) + 구조 |
| RS-B7 | 탈퇴 참가자 nickname 익명 표시·행 보존 | 조회 SQL 조인 시 nickname 해석(user.nickname 이미 "탈퇴한 러너") |

---

## 3. Participation (B2-S3)

### 3.1 상태 enum — B2 구현 범위

`Participation` = {`REGISTERED`, `STARTED`, `FINISHED`, `DNF`, `DNS`, `WITHDRAWN`}
- **B2 구현**: `REGISTERED`(신청), `STARTED`(시작 신호). `WITHDRAWN`은 **유저 탈퇴 시 행 보존 표시**(익명화 — nickname만 바뀜, participation.status 자체는 B2에서 능동 전이 없음).
- **M2**: `FINISHED`/`DNF`(업로드·FinishPolicy), `DNS`(마감 시 미출주 판정).
- enum 값 집합 자체는 계약에 **전량 명시**(R-001) — 클라는 미구현 값도 `unknown` 폴백으로 안전 파싱.

### 3.2 register (opt-in 신청 — J-1)

- `POST /sessions/{id}/register`: 호출자 자신을 REGISTERED 등록. **OPEN 세션만**(§2.4). **크루 ACTIVE 멤버만**(비멤버 403). 멱등 — 이미 REGISTERED/STARTED면 no-op 200(중복 신청 무해). UQ(session_id,user_id) 제약과 정합.
- DNS 의미론 성립: 신청(REGISTERED)했으나 마감까지 미출주 → M2에서 DNS 판정.

### 3.3 start (STARTED 신호 — 멱등, 유실 무해)

- `POST /sessions/{id}/start`: 호출자 participation을 STARTED로. **선 register 필요**(participation 부재 시 409 `SESSION_STATE_INVALID` — opt-in 원칙상 자동 등록 안 함). 세션 OPEN/RUNNING만.
- **멱등**: 이미 STARTED/FINISHED면 no-op(재호출 무해). **서버 다운 허용 설계** — 신호 유실돼도 M2 업로드가 진실(started_at은 track_record가 보유). start는 "지금 뛰는 중" 읽기 표시(B2-C1 ③)·RUNNING 전이 유발용 보조 신호.
- **세션 전이 부수효과**: 세션이 OPEN이고 이번이 최초 STARTED면 OPEN→RUNNING(§2.2).

### 3.4 취소 정책 (cancel)

- `POST /sessions/{id}/cancel`(크루장 전용): 세션 status → CANCELLED(DRAFT/OPEN/RUNNING에서). **순위·보상 미생성**.
- **participation 행 미변경**(현 REGISTERED/STARTED 보존) — 세션이 CANCELLED이므로 참가 상태는 무의미해지나 이력 보존. B2 시점 트랙 없음 → "뛰던 참가자 트랙 개인기록 보존"은 M2 트랙 등장 시 유효(취소 자체는 세션 상태만 정리).

### 3.5 Participation 불변식 체크리스트 (qa용)

| # | 불변식 | 강제 수단 |
|---|---|---|
| PA-B1 | register는 OPEN 세션·크루 ACTIVE 멤버만, 멱등(UQ session_id,user_id) | 코드 + 스키마 UQ |
| PA-B2 | start 멱등(STARTED/FINISHED면 no-op), 선 register 필요 | 코드 |
| PA-B3 | 최초 start가 세션 OPEN→RUNNING 전이 | 코드 |
| PA-B4 | start 신호 유실 무해(서버 다운 허용) — 진실은 M2 track_record | 설계 원칙(M2 재검증) |
| PA-B5 | cancel은 participation 미변경, 순위·보상 미생성 | 코드 |
| PA-B6 | 탈퇴 유저 participation 행 보존(익명 표시) | FK RESTRICT + nickname 조인 |

---

## 4. 클라이언트 경계 (B2-C1/C2/C3)

### 4.1 Race DTO (`lib/core/model/`, enum_codec 폴백 경유)

- `SessionSummary`(목록), `SessionDetail`(course 요약 + participants), `CourseSummary`, `CourseDetail`, `ParticipantView`.
- **enum**: `RaceStatus`(6값+unknown), `ParticipationStatus`(6값+unknown) — B1 `enum_codec` `parseEnum` + `unknown` 폴백. **계약 값 집합 대조 테스트** 추가(R-001 상시 장치 확장 — B1 `enum_contract_test` 패턴).
- 송신 enum 폴백 금지(B1 §6.4 유지) — 클라가 서버로 enum 보내는 경로는 B2에 없음(register/start/cancel/open 모두 body 없는 명령).

### 4.2 CoursePolylineMap 추상화 경계 (B2-C3)

- **인터페이스**(트래킹 격리 패턴 준용): `CoursePolylineMap` — 폴리라인 좌표열 표시 전용 위젯 추상. 입력 = 디코딩 좌표열(`List<LatLng>`) + start/finish 마커.
- **placeholder 구현**: 정적 폴리라인 스케치(실 tile 없음) — 코스 상세·선택 미리보기.
- **대기 고정점**: `flutter_naver_map` 의존성·실 어댑터·경로 그리기 UI·Client ID 주입은 대기(§계획 §4). 어댑터 교체 지점·Client ID 주입 지점만 주석. **R-002 가드**: 지도 SDK 유입돼도 `lib/core` 순수성 유지(어댑터 레이어 한정).
- 폴리라인 디코딩은 클라 `PolylineCodec.decode`(순수, `lib/core/geo`) 재사용 — 지도 위젯은 좌표만 받음.

### 4.3 세션 화면 데이터 요구

- **목록**: GET `/crews/{id}/sessions` → `SessionSummary`(course_name·status·scheduled_at·upload_deadline·participant_count). 크루 상세에서 진입.
- **상세**: GET `/sessions/{id}` → course 요약(폴리라인 미리보기)·participants(status별 렌더, **STARTED="지금 뛰는 중"** 읽기 표시)·일시·마감.
- **생성**(크루장): POST `/crews/{id}/sessions` — 코스 선택(**GET courses 시드 목록**)·일시·마감(scheduled+12h 기본 제시·수정 가능).
- **'레이스 시작' 진입점**: **활성 버튼 — STARTED 신호 API 호출 + "트래킹은 M2" 안내** (W26-1 정본 확정, 오케스트레이터 판정 2026-07-04: 계약 §6 start=라이브 엔드포인트·다운스트림 무해(QA 4차)·라이브 곡선 검증 필요성 근거. M2에서 이 버튼이 실 트래킹 시작으로 확장). 초안의 disabled stub 표기는 폐기.

---

## 5. 계약 산출 (B2-S4)

- `docs/contracts/course-api.md` **신규 v0.1** — 목록/상세/생성, 폴리라인 1e5·tie 규약, 발행 후 불변 의미론, 오류코드.
- `docs/contracts/session-api.md` **v0.1 → v0.2** — 조회 세트(생성/목록/상세) shape 확정 + open/register/start/cancel 명령 append, enum·오류 전수, 변경 이력 주석.
- `docs/contracts/conventions.md` — **무변경**. §4 code 집합에 `COURSE_IMMUTABLE`·`SESSION_STATE_INVALID` 이미 존재(v0.1). B2 신규 code 없음.

---

## 6. QA 검증 포인트 (이월 흡수 + B2 신규)

| 항목 | 대상 |
|---|---|
| 폴리라인 1e5/1e6 서버·클라 대조(QA 3차 이월 6) | CO-B1/B2 — B2-T2 상호운용 골든 **종결 박제** |
| RaceStatus 3자 대조(QA 3차 이월) | 서버 enum ↔ 계약 ↔ 클라 DTO(§4.1). 클라 **로컬** 상태머신은 M2 |
| Participation enum 3자 대조 | 6값 전수 — 미구현값(FINISHED/DNF/DNS)도 계약·클라 집합 보존 |
| 상태머신 합법/불법 전이 | §2.4 매트릭스 전수(RS-B1) |
| 멱등(register/start) | PA-B1/B2 |
| 발행 후 불변 | CO-B5(구조·FK) |
| dev 시드 prod 미유출 | CO-B7 |
| 공유 픽스처(서버 응답↔앱 파싱) CI화(P16-1) | session/course 신규 계약부터(B2-T1 ④) |

---

## 7. backend / flutter 주의사항

**backend-dev (2)**:
- 폴리라인 서버 디코딩은 **1e5**(1e6 아님) + tie **half-away-from-zero** — `Math.round()` 금지(음좌표 갈림). B2-T2 클라 골든 벡터로 상호운용 박제.
- dev 시드 코스는 **`@Profile` 시더 빈**(Flyway 마이그레이션은 prod에도 실행되므로 지양). RUNNING 진입은 첫 STARTED 신호가 유발(scheduled_at 자동 아님 — 스케줄러는 M2).

**flutter-dev (2)**:
- RaceStatus·ParticipationStatus는 6값 전수 파싱 + `unknown` 폴백 + 계약 대조 테스트(미구현 상태값 FINISHED/DNF/DNS도 집합 보존). register/start/cancel/open은 **body 없는 명령**(경로+토큰만).
- `CoursePolylineMap`은 인터페이스+placeholder만 — `flutter_naver_map`·Client ID 대기. 폴리라인 디코딩은 `lib/core/geo/PolylineCodec` 재사용(R-002 core 순수성 유지).

---

## 8. 미규정 잔여

- **미규정-1 (dev 시드 대상)**: 시드 코스를 어느 크루에 붙일지 — dev 첫 크루 or 시더 데모 크루 자체 생성. **제안**: backend-dev 재량, prod 미유출만 불변(CO-B7). 에스컬레이션 불요.
- **미규정-2 (M2 — OPEN→FINALIZING 직행)**: STARTED 신호 유실 시 RUNNING을 건너뛴 세션의 마감 전이. **제안**: M2 마감 스케줄러가 OPEN·RUNNING 모두에서 FINALIZING 진입 허용(신호 유실 내성). M2 설계 시 확정.
- **미규정-3 (RUNNING 후 지참)**: RUNNING 세션에 신규 register 차단(§2.4 ²). **제안**: 시작된 레이스 명단 추가는 혼란 → 차단 유지. 필요 시 planner 판단(늦참 허용 정책은 계획서 미규정).
- **미규정-4 (M2 — FINALIZING 취소)**: FINALIZING 중 cancel. **제안**: M2에서 결정(이미 산정 결과 폐기 동반). B2는 종료 전 3상태만 취소.
- **잔여 (B1 계속)**: ReplaySnapshot payload 표시명 내장 여부(M2 재점검) — 12 §1.3 제안 유지.
