# 06 — QA 검증 보고

> 1차(flutter)와 2차(backend) 회차를 누적 기록한다. 최신 회차가 위.

---

# 2차 — 백엔드 (배치 A: 골조 + 계약)

> 작성: qa · 날짜: 2026-07-04 · 대상: `backend/` 배치 A
> 기준: `docs/contracts/`(conventions.md, app-version.md), `02_analyst_design.md` §2, `03_backend_report.md` §5(검증 경계면 5종)
> 클라이언트 HTTP 소비는 미구현 → app-version은 계약↔서버 **2자 대조**(3자 대조는 앱 DTO 구현 후 재수행).

## 판정 요약 (2차)

**차단 1건 / 경고 0건 / 참고 3건** + 이전 발견 재검증 1건(R-002 CLOSED 확인)

### 이전 발견 재검증 (회귀 확인 — 신규 범위에 앞서 수행)
- **R-002 (1차 경고 W-1)**: `no_platform_imports_test.dart` 가 **allowlist 방식으로 전환됨** 확인(`dart:*` + 상대경로만 허용, `package:` 전부 실패). 실행 재검증: 가드 테스트 통과 + **독립 캐너리**(`lib/core/`에 `package:dio` import 임시 삽입 → 가드 실패 확인 → 즉시 제거)로 검출 동작 입증. 수정 + 재발 방지 장치 모두 확인 → **CLOSED 유지 타당**.

| 검증 범위 | 결과 | 방식 |
|---|---|---|
| 1. app-version 계약 대조 (계약 ↔ 컨트롤러·DTO·오류 shape) | 통과 | 정적 + 슬라이스 테스트 재실행 |
| 2. Flyway V1 ↔ 설계 §2 (17테이블 전수 대조) | **차단 B2-1** (정의는 일치, DDL이 MySQL 8에서 실행 불가) | 정적 + **실행(라이브 재현)** |
| 3. 헥사고날 불변식 (domain 패키지 spring/jakarta import 0건) | 통과 | 정적 |
| 4. 실행 검증 (build / compose / 기동 / curl) | build·MySQL·Flyway까지 실행 — **앱 기동은 B2-1로 실패**, curl 미도달 | 실행 |
| 5. 시간대 규약 (JVM UTC·MySQL 세션·Jackson) | 통과 | 정적 + 라이브(MySQL 세션 확인) |

## 차단 B2-1 — `rank` 는 MySQL 8 예약어: Flyway V1 마이그레이션 실패 → 앱 기동 불가

- **심각도**: 차단 (레지스트리 R-003 등록)
- **경계면**: Flyway `V1__init.sql` ↔ MySQL 8.0 (설계 §2.12·§2.14)
- **위치**: `backend/src/main/resources/db/migration/V1__init.sql:192` (`rank_entry.rank INT NULL` — 미인용), 같은 문제 `V1__init.sql:231` (`reward_item.rank INT NOT NULL`)
- **원인**: `RANK` 는 MySQL 8.0.2+에서 윈도 함수용 **예약어**. 백틱 없이 컬럼명으로 쓰면 구문 오류. (`user` 테이블은 백틱 처리했으나 `rank` 는 누락.)
- **실행 재현 (정황 아님 — 라이브 확인)**:
  1. `docker compose up -d mysql` → healthy (MySQL 8.0.46, 세션/글로벌 타임존 `+00:00` 확인).
  2. 최소 재현: `CREATE TABLE qa_repro (rank INT NULL);` → **ERROR 1064 (42000)**.
  3. `./gradlew bootRun` (로컬 기동, 기본 datasource=localhost 컨테이너) → Flyway `Migrating schema runningcrew to version "1 - init"` 후 **`SQLSyntaxErrorException ... near 'rank INT NULL' / SQL State 42000 / Error Code 1064`** → `flywayInitializer` 빈 생성 실패 → **애플리케이션 부팅 실패(exit 1)**.
  4. 결과: 17개 중 **12개 테이블만 생성**, `rank_entry`·`replay_snapshot`·`reward_plan`·`reward_item`·`reward_grant` 5개 미생성. MySQL DDL은 비트랜잭션이라 부분 적용 상태로 중단(flyway_schema_history에 실패 기록).
- **영향**: backend-dev §5 경계면 3(Flyway 적용)·4(Compose 기동) **자동 실패**. `restart: unless-stopped` 무인 기동 전제가 첫 배포에서 즉시 깨진다. 라이브 app-version curl·actuator health 검증도 이에 막혀 미도달.
- **어느 쪽이 틀렸나**: 설계문서 §2.12/§2.14가 컬럼명 `rank` 를 예약어 주의 없이 지정 → SQL이 그대로 따름. 계약(docs/contracts/)은 무관(이 컬럼은 아직 API에 노출 안 됨).
- **권고** (수정은 backend-dev, 설계 표기는 domain-analyst):
  - 즉시 수정: V1__init.sql 두 곳 백틱 처리 (`` `rank` ``). 부분 적용된 DB는 개발 환경이므로 volume 재생성으로 정리(이번 QA는 `compose down -v` 로 원복 완료).
  - 배치 B 주의: rank_entry/reward_item JPA 엔티티 매핑 시 `@Column(name = "\`rank\`")` 또는 Hibernate `globally_quoted_identifiers` 필요 — 컬럼명 유지 시. 대안은 컬럼명 변경(`rank_no` 등, domain-analyst 경유 설계 갱신).
  - 재발 방지 장치: "Flyway 마이그레이션을 라이브 MySQL에 적용하는 검증"을 CI/QA 상시 절차로 (Testcontainers 마이그레이션 테스트가 이상적 — 슬라이스 테스트는 DB 미접촉이라 이 유형을 영원히 못 잡는다).

## 2차 상세

### 1. app-version 계약 대조 — 통과 (2자: 계약 ↔ 서버. 클라 구현 없음)

| 계약 app-version.md | 서버 구현 | 판정 |
|---|---|---|
| `GET /api/v1/app-version?platform=` (인증 불요) | `AppVersionController` `@RequestMapping("/api/v1/app-version")` + `@RequestParam Platform` | 일치 |
| 200: `platform`/`min_version`/`updated_at` (snake_case) | `AppVersionResponse(platform, minVersion, updatedAt)` + 전역 `property-naming-strategy: SNAKE_CASE` | 일치 — 슬라이스 테스트가 `$.min_version`, `$.updated_at`=`"2026-07-01T00:00:00Z"` 검증 |
| `updated_at` UTC ISO-8601(Z) | Jackson `WRITE_DATES_AS_TIMESTAMPS` off + timeZone UTC (yml + TimeConfig 이중 고정) | 일치 |
| 400 `VALIDATION_ERROR` (누락·미지 값) | `GlobalExceptionHandler` — Missing/TypeMismatch → `{code:"VALIDATION_ERROR", message}` | 일치 (테스트: 누락·`WINDOWS` 둘 다) |
| 404 `NOT_FOUND` (레코드 없음 — 계약 "제안" 채택) | `AppVersionService` → `ApiException(NOT_FOUND)` | 일치 |
| 오류 shape `{code, message}` (conventions §4) | `ApiError(String code, String message)` record | 일치 |
| Platform enum {ANDROID, IOS} | `Platform` enum 동일 | 일치 |

- `ErrorCode` 11종 = conventions §4 초안 집합과 1:1. HTTP 매핑(400/401/403/404/409)도 §4 표와 일치.
- `PageResponse(items, page, size, totalElements, totalPages)` = conventions §6 래퍼와 일치(snake_case 전역 적용으로 `total_elements`/`total_pages`).
- **테스트 독립 재실행**: `./gradlew test --rerun-tasks` → BUILD SUCCESSFUL (4 테스트). `./gradlew build` 재현 성공.
- **라이브 curl 실응답 대조는 미수행** — B2-1(부팅 불가)에 막힘. 수정 후 재검증 항목.

### 2. Flyway V1 ↔ 설계 §2 — 정의 대조는 전수 일치 (차단은 실행 가능성 문제)

17개 테이블(§2.14=plan+item 2개) 컬럼·타입·NOT NULL·기본값·인덱스·FK를 전수 대조 — **불일치 0건**. 특기:
- `track_payload`: PK=FK(`track_record_id`), raw NN/refined NULL, `ON DELETE CASCADE` — 설계 §2.10 그대로.
- `rank_entry`: `user_id` FK **RESTRICT** 명시(주석 "CASCADE 절대 금지" 포함) — U-2 탈퇴 익명화 방어선 유지.
- FK 정책 **라이브 확인**(마이그레이션이 생성한 12개 테이블 information_schema 조회): `device_token`·`track_payload`=CASCADE, 나머지 전부 RESTRICT — 설계 §2 규약과 일치. (rank_entry 등 5개는 B2-1로 미생성 → 수정 후 라이브 재확인 필요.)
- 참고 P2-2: 시각 컬럼 `TIMESTAMP(6)`은 MySQL 특성상 2038년 상한 — 설계 §2가 명시 선택했으므로 위반 아님(기록만).

### 3. 헥사고날 불변식 — 통과 (정적, 단 현재는 공허 충족)

6개 컨텍스트 `*/domain/` 패키지는 전부 `package-info.java`뿐 — spring/jakarta/jpa import **0건** (grep 전수 확인). backend-dev §5-5의 자평대로 "빈 골조라 자동 충족" — **배치 B에서 도메인 클래스가 생기면 실질 검증으로 재수행 필요**(현재는 가드 테스트/ArchUnit 부재, 참고 P2-1).

### 4. 실행 검증 수행 내역 (조용한 생략 없음)

| 단계 | 수행 | 결과 |
|---|---|---|
| `./gradlew build` + `test --rerun-tasks` | 실행 | 성공 (4 테스트) |
| `docker compose up -d mysql` → 헬스 | 실행 | healthy (8.0.46) |
| 앱 기동 | 실행 — 단 **컨테이너 빌드 대신 `./gradlew bootRun`**(로컬 JVM→컨테이너 MySQL). 이미지 빌드(JDK25 pull)는 시간상 생략 — **앱 컨테이너 자체 기동은 미검증** 명시 | Flyway 실패로 부팅 실패 (B2-1) |
| curl `/actuator/health`·`/api/v1/app-version` | **미도달** (부팅 실패) | B2-1 수정 후 재검증 |
| compose down -v | 실행 | 환경 원복 완료 |

### 5. 시간대 규약 — 통과

- JVM: `RunningCrewApplication.main` 이 `SpringApplication.run` **이전에** `TimeZone.setDefault(UTC)` — 순서 올바름.
- MySQL: compose `--default-time-zone=+00:00` + JDBC `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` + `hibernate.jdbc.time_zone: UTC` — 3중 고정. **라이브 확인**: `@@session.time_zone`/`@@global.time_zone` = `+00:00`.
- Jackson: yml + `TimeConfig` customizer 이중 명시(회귀 방지 주석까지) — 정합.
- Instant 왕복(저장→조회 오프셋 무손실)은 DB 접촉 테스트가 없어 **미검증** — B2-1 수정 후 라이브로 확인 권장.
- 참고 P2-3: `logging.pattern.console` 의 `'Z'` 는 리터럴 — JVM UTC 고정(main) 전제로만 참. main 을 거치지 않는 실행(일부 테스트·툴)에선 타임스탬프가 로컬시각+Z 로 표기될 수 있음. 운영 JSON 인코더 배선(보류-2) 때 `%d{...,UTC}` 명시 권장.

### 2차 → 3차(배치 B) 이월 항목

1. **B2-1 수정 재검증**: 백틱 수정 후 compose 전체 기동(앱 컨테이너 포함) → 17테이블 + rank_entry RESTRICT 라이브 확인 → curl 실응답 vs app-version.md diff.
2. 앱 DTO 구현 후 app-version **3자 대조** 완성 (1차 이월 항목과 병합).
3. 헥사고날 불변식 실질 검증(도메인 클래스 생성 후) + ArchUnit 등 자동 가드 도입 검토.
4. `ddl-auto: none → validate` 승격 확인(보류-1), Instant 왕복 라이브 검증.
5. rank_entry/reward_item JPA 매핑의 `rank` 인용 처리 확인 (B2-1 파생).

---

# 1차 — flutter 모듈 (배치 A: 클라이언트 골조)

> 작성: qa · 날짜: 2026-07-04 · 대상: `app/` 배치 A (클라이언트 골조)
> 기준: `02_analyst_design.md` §3(시그니처)·§4(불변식), `04_flutter_report.md`, domain-model·qa-integration 스킬
> 백엔드는 구현 중 → 앱↔서버 3자 대조는 **2차 QA로 이월**. 이번 회차는 클라 내부 경계(인터페이스 격리·플랫폼 격리·로컬 우선·스파이크 보존)만.

## 판정 요약

**통과 (차단 0건 / 경고 1건 / 참고 3건)**

| 검증 범위 | 결과 | 방식 |
|---|---|---|
| 1. 인터페이스 격리 3자 대조 (§3.2~3.4 ↔ 인터페이스 ↔ Android 구현) | 통과 | 정적 |
| 2. 플랫폼 격리 불변식 (core 플랫폼 import 0건 + 가드 유효성) | 통과 (경고 1) | 정적+실행 |
| 3. 로컬 우선 불변식 (업로드 성공 전 삭제 경로 부재) | 통과 | 정적 |
| 4. 스파이크 보존 (/spike, 검증절차 유효성) | 통과 (참고) | 정적 |
| 5. 실행 검증 (analyze + test) | 통과 | 실행 |
| 재검증: regressions.md OPEN 항목 | OPEN 0건 (R-001 예시뿐) | — |

---

## 1. 인터페이스 격리 3자 대조 — 통과 (정적)

설계 §3.2~3.4 시그니처 ↔ `lib/platform/*/`(인터페이스) ↔ Android 구현체를 메서드·타입·스트림 단위로 대조. **전 항목 일치.**

### 1.1 LocationTracker (`lib/platform/location/location_tracker.dart` ↔ `android_foreground_tracker.dart`)
| 설계 §3.2 | 인터페이스 | Android 구현 | 판정 |
|---|---|---|---|
| `enum TrackerState {ready,running,paused,stopped}` | L6 동일 | — | 일치 |
| `TrackerState get state` | L15 | L36-37 `@override` | 일치 |
| `Stream<TrackerState> get stateChanges` | L19 | L39-40 | 일치 |
| `Stream<TrackPoint> get points` | L23 | L42-43 | 일치 |
| `Future<void> start/pause/resume/stop/dispose()` | L27~40 | L51/70/76/81/88 전부 `@override` | 일치 |
| `resume` 포함(설계 M-1 확정) | L33 있음 | L76-79 있음 | 일치 — 상태머신 폐합 |

- TrackerState에 `FINISHED_LOCAL/UPLOADED` **미포함** = 도메인 규범 준수(그 상태는 서버가 모르는 업로드 계층 소관, 트래커에 넣으면 안 됨). 인터페이스 주석 L3-5가 이 경계를 명시. 정합.
- 구현 `start()`(L52-53) 가드는 running·paused에서 재호출 무시 → 설계 "running 중 재호출 무시"와 정합(paused 추가 무시도 안전).

### 1.2 NotificationService — 시그니처 3메서드(start/update/stopTrackingNotification) §3.3와 완전 일치.
### 1.3 PermissionService — enum 4값·`TrackingPermissions`(location/notification/canTrack)·5메서드 §3.4와 완전 일치. `restricted`는 Android 매퍼가 산출 안 함(iOS용 예약) — 정상.

---

## 2. 플랫폼 격리 불변식 — 통과, 경고 1건

### 통과 근거 (정적+실행)
- `lib/core/` 전체 import 감사: `dart:convert`, `dart:io`(track_store만), 나머지는 전부 상대 import. **플랫폼 패키지(geolocator/flutter_foreground_task/path_provider)·`package:flutter`·`dart:ui`/`dart:isolate`/`dart:ffi` 0건** (직접 grep 재확인).
- 플랫폼 패키지 import는 `lib/platform/` 구현체 + `lib/spike/` + `lib/main.dart`(composition root의 `initCommunicationPort` 1회)에만 존재 — core 유출 없음.
- 가드 테스트 `test/core/no_platform_imports_test.dart` 존재·통과.

### 판정: track_store의 `dart:io`는 허용 범위인가 → **허용 (정합)**
`lib/core/tracking/track_store.dart:2`가 `dart:io`(File) 사용. 가드 테스트 L9 주석이 "dart:io는 저장 모듈의 파일 IO라 허용 — 플랫폼 채널 아님"으로 의도를 명시. `dart:io` File API는 Android/iOS/desktop 공통 크로스플랫폼 API(geolocator 같은 OS 플랫폼 채널과 성격이 다름)이고, 설계 §3.5가 저장을 `core/storage`로 두는 것과 일관. **격리 위반 아님.** (참고 P-3: 위치는 아직 `core/tracking`, `core/storage` 승격은 배치 B 이월 — 04 보고 §6과 일치.)

### 경고 W-1 — 격리 가드가 **denylist**라 배치 B HTTP 유입을 못 잡는다
- **증상/위치**: `no_platform_imports_test.dart:12-17`의 `forbidden` 목록이 4개 고정 문자열(geolocator/flutter_foreground_task/path_provider/`package:flutter/`)만 검사하는 **금지목록 방식**. 목록 밖 패키지는 무엇이든 통과한다.
- **재현**: `lib/core/upload/upload_queue.dart`에 `import 'package:dio/dio.dart';`를 넣어도 가드는 실패하지 않는다(dio가 목록에 없음). `package:flutter/`는 슬래시 포함이라 `package:flutter_riverpod`도 검출 대상 아님.
- **왜 지금 문제인가**: 배치 B가 `dio` 실 전송을 배선한다. 설계상 HTTP는 어댑터로 가고 `upload_queue`는 순수 유지가 원칙(04 §5, 설계 §3.5)이나, **자동 방어선이 없어** dio가 core로 새도 CI가 초록이다. C-5(로컬 우선)와 순수함수 골든 경계가 동시에 무너질 수 있는 지점.
- **현재 실제 위반은 0건** — 잠재적/선행 위험(경고).
- **권고**(수정은 flutter-dev): 가드를 allowlist로 전환(`dart:core/convert/io/async/math` + 상대경로만 허용, 그 외 `package:`/`dart:ui` 전부 실패) — 또는 최소한 배치 B 착수 전 `package:dio`를 forbidden에 추가. 레지스트리 R-002 등록.

---

## 3. 로컬 우선 불변식 (C-5) — 통과 (정적)

업로드 성공 확정 전 로컬 데이터 삭제 경로를 전수 수색.
- `lib/` 전체에서 삭제·제거 호출은 **단 2곳**: `track_buffer.dart:48 _points.clear()`(drain이 내용 반환 **후** 메모리 버퍼 비움 — 디스크 아님), `upload_queue.dart:134 removeWhere(succeeded)`(succeeded 상태만 제거).
- `UploadQueue`: `markFailed`는 maxAttempts 소진 시에도 `failed`로 **보존**(L110-112), 큐에서 안 버림. `purgeSucceeded`는 succeeded만 제거(L133-135). 성공 전 제거 경로 없음.
- `TrackStore`: **삭제 메서드 자체가 없다**(append/readAll/listSessionFiles만). 로컬 원본을 지울 API가 없으므로 성공 전 삭제가 구조적으로 불가능. 강한 정합.
- 재검증 테스트 `upload_queue_test.dart`에 "maxAttempts 초과여도 failed 보존(C-5)"·"성공 확정 후에만 purge" 존재. 회귀 방지선 있음.

---

## 4. 스파이크 보존 — 통과, 참고 2건

- `/spike` 라우트 유지(`router.dart:19-22`), `lib/spike/spike_screen.dart`·`tracking_task_handler.dart` 존재·무수정.
- `docs/m1_spike_검증절차.md` 절차 유효성: adb 명령이 참조하는 패키지 `com.example.running`이 실제 `android/app/build.gradle.kts`의 applicationId/namespace와 **일치** → 검증절차 절 "수집 데이터 확인·회수"의 `run-as com.example.running` 그대로 유효.
- 진입 경로만 홈→`/spike` 1단계 추가(04 §3과 일치), 트래킹 로직 무변경.

- **참고 P-1**: 스파이크는 `channelId='tracking_spike'`, `AndroidForegroundTracker`는 `channelId='tracking'`로 채널 분리 → 충돌 없음. 단 **serviceId는 둘 다 `1`**(spike_screen.dart:98, android_foreground_tracker.dart:126). 두 경로는 동시 실행되지 않으므로 현재 무해하나, 배치 B에서 트래커 실배선 후에도 스파이크가 살아있는 동안은 "동시 기동 금지"를 유지해야 한다.
- **참고 P-2**: `AndroidForegroundTracker` 실기기 1시간 유실 테스트는 신규 isolate 콜백(`androidTrackerCallback`) 경로라 **미수행**(04 §3 명시). 스파이크와 동일 메커니즘이나 별도 진입점이므로, 배치 B 실배선 시점에 스파이크와 동등한 유실 테스트 1회 재수행 필요 — 자동 검증 불가, 실기기 수동 항목.

---

## 5. 실행 검증 — 통과 (실행)

- `flutter analyze`: **No issues found!** (0 이슈). 04 보고 주장과 일치.
- `flutter test`: **All tests passed (30/30)**. 04 보고 주장과 일치. (가드 테스트·upload_queue C-5 테스트·track_store UTC 직렬화 테스트 포함.)

---

## 6. 참고 P-4 — NotificationService.startTrackingNotification의 플랫폼 간 의미 분기

`AndroidNotificationService.startTrackingNotification`(android_notification_service.dart:19-30)은 서비스가 이미 떠 있을 때만 내용 갱신하고, **알림을 실제로 시작하지 않는다**(서비스 lifecycle=알림 표시는 `AndroidForegroundTracker.start()` 소유). 설계 §3.3 계약의 "상시 알림 시작"과 Android 구현 의미가 갈린다 — 문서화된 의도적 결합(04 §1.2, 구현 주석 명시)이라 **버그 아님**. 다만 배치 B에서 상위 레이어가 트래킹을 배선할 때 **`tracker.start()`와 `notification.startTrackingNotification()`을 둘 다 호출**해야 iOS(실제 알림 시작) 확장에서 대칭이 성립. 2차 QA(배선 시점)에서 호출 순서 재확인 대상.

---

## 7. 백엔드 2차 QA 때 함께 볼 항목 (이월)

1. **앱↔서버 3자 대조**: `docs/contracts/`(conventions/app-version/crew-api/session-api) ↔ 서버 컨트롤러·record ↔ 앱 DTO/파서. 이번 배치는 앱 HTTP·DTO 미구현이라 대조 불가(04 §2: "이번 배치 서버 HTTP 호출 없음").
2. **enum 미지값 폴백**: 04 §2가 "배치 B DTO 파싱에서 적용 예정"이라 명시 — RaceStatus/Participation 파서의 문자열 집합 ↔ 계약 값 집합 대조(R-001 유형).
3. **W-1 후속**: dio 실배선 후 `core/upload`가 순수 유지됐는지 + 가드 allowlist화 여부 재확인.
4. **P-4 배선 검증**: 트래킹 UI 배선 시 notification start/stop 대칭 호출.
5. **P-2 실기기**: 트래커 경로 1시간 유실 테스트 결과.
6. **폴리라인 정밀도**: `PolylineCodec`(1e-5) ↔ 서버 인코딩 라이브러리 기본값 대조(1e5 vs 1e6 10배 틀어짐 패턴).
</content>
</invoke>
