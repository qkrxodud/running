# 02 — 도메인 설계·계약 확정안 (배치 A: 골조 + 계약)

> 작성: domain-analyst · 날짜: 2026-07-04 · 기준: `01_planner_plan.md`(배치 A), `domain-model` 스킬, 계획서 §5~§7
> 범위: 배치 A에서 실제로 **만들어지는 구조물**의 설계 확정 — 백엔드 패키지 골조, DB V1 스키마, 클라이언트 3인터페이스 + 순수 Dart 코어. 도메인 로직 구현(애그리거트·상태머신)은 배치 B이므로 여기선 **스키마·계약·경계면**만 확정한다.

---

## 0. 이번 배치가 확정하는 것 / 미루는 것

| 확정(배치 A) | 미룸(배치 B 이후) |
|---|---|
| 15개 테이블 DDL(컬럼·타입·NOT NULL·인덱스·FK) | 애그리거트/도메인 서비스 구현 |
| 시간대 규약(UTC 저장), enum=VARCHAR 규약 | 상태머신 전이 코드, 이벤트 리스너 |
| 조회 계약(crew/session/app-version) shape | 명령 계약 상세(로그인·업로드·STARTED) |
| 클라이언트 3인터페이스 시그니처 | 인터페이스 구현체(AndroidForegroundTracker 등) |
| 순수 Dart 코어 모듈 경계 | 실 트래킹 파이프라인 배선 |

계약은 **"계약 우선" 초안(v0.1)** — 서버 구현이 없어도 shape가 진실이다.

---

## 1. 백엔드 패키지 골조 (A-B1)

`backend-hexagonal` 스킬 그대로. 루트 `com.runningcrew`, 6개 컨텍스트 + `replay` + `common`.

```
com.runningcrew
├── user | crew | race | tracking | ranking | reward   # 각각 domain / application / adapter{in.web, out.persistence}
├── replay          # 프로젝션 — 애그리거트 없음
└── common          # 공용 VO(좌표·폴리라인), 이벤트 발행 지원, 시각/직렬화 설정, 공통 오류 응답
```

배치 A에서는 **빈 골격 + 부팅 가능**까지만(도메인 클래스는 배치 B). `common`에 다음 3종은 배치 A에 실체를 둔다:
- `TimeConfig` — Jackson `Instant` UTC 직렬화(오프셋 명시), JPA `Instant` 매핑. (A-B4)
- `ApiError`(record: `code`, `message`) + `@RestControllerAdvice` 뼈대. (계약 conventions.md 근거)
- `PageResponse<T>`(계약 페이지네이션 형식과 1:1).

**컨텍스트 배치 근거**: `app_min_version`은 어느 도메인에도 속하지 않는 운영 메타 → `common`(또는 별도 `platform`)에 읽기 전용 어댑터로 둔다. 서버 플랫폼 무지 원칙과 무관(플랫폼은 클라 식별용 파라미터일 뿐 도메인 결합 아님).

---

## 2. DB 초기 마이그레이션 스키마 (A-B3, Flyway V1)

규약(전 테이블 공통):
- **시각 컬럼은 전부 `TIMESTAMP(6)` NULL/ NOT NULL 구분**, 저장·비교·판정 전부 **UTC**. 앱서버 JVM·MySQL 세션 타임존을 UTC로 고정(`connectionTimeZone=UTC`). KST 표시는 클라 소관.
- **enum은 전부 `VARCHAR`(문자열)** — `@Enumerated(STRING)` 대응. ORDINAL 금지.
- PK는 `BIGINT AUTO_INCREMENT`(`id`), 예외: `invite_code`(자연키 `code`), `app_min_version`(자연키 `platform`), `track_payload`(FK가 곧 PK).
- 문자셋 `utf8mb4`, 엔진 InnoDB.
- FK는 전부 명시. `ON DELETE`는 기본 `RESTRICT`(도메인 삭제는 애플리케이션이 익명화/삭제 순서를 제어 — R:User 탈퇴 불변식 때문에 DB CASCADE 금지). 예외는 표에 명시.

> 표기: `NN`=NOT NULL, `IDX`=인덱스, `UQ`=유니크, `FK→`=외래키. 타입 뒤 괄호는 근거/비고.

### 2.1 user
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| nickname | VARCHAR(30) | NN | 탈퇴 시 "탈퇴한 러너"류로 익명화(값 덮어쓰기) |
| kakao_id | VARCHAR(64) | UQ(NULL 허용) | 탈퇴 시 NULL로 파기. UQ는 활성 계정 중복가입 방지 |
| status | VARCHAR(16) | NN, default 'ACTIVE' | enum {ACTIVE, WITHDRAWN} |
| created_at | TIMESTAMP(6) | NN | UTC |
| withdrawn_at | TIMESTAMP(6) | NULL | 탈퇴 시각. 미규정 아님(스킬 명시 컬럼) |

인덱스: `UQ(kakao_id)`, `IDX(status)`.

### 2.2 device_token
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| user_id | BIGINT | NN, FK→user(id) | |
| fcm_token | VARCHAR(255) | NN | 탈퇴 시 행 삭제(식별정보) |
| platform | VARCHAR(16) | NN | enum {ANDROID, IOS}. 스킬 "누락주의 컬럼" |
| updated_at | TIMESTAMP(6) | NN | UTC |

인덱스: `IDX(user_id)`, `UQ(fcm_token)`(동일 토큰 중복 등록 방지 — 미규정, 제안). FK `ON DELETE CASCADE` 허용(토큰은 순수 식별정보, 유저 파기 시 동반 소멸이 불변식과 일치).

### 2.3 crew
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| name | VARCHAR(50) | NN | |
| leader_id | BIGINT | NN, FK→user(id) | **크루장 항상 1명** — NN이 그 일부를 강제 |
| status | VARCHAR(16) | NN, default 'ACTIVE' | enum {ACTIVE, CLOSED}. 마지막 1인 탈퇴 시 CLOSED |
| created_at | TIMESTAMP(6) | NN | UTC |

인덱스: `IDX(leader_id)`, `IDX(status)`.

### 2.4 crew_member
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| crew_id | BIGINT | NN, FK→crew(id) | |
| user_id | BIGINT | NN, FK→user(id) | |
| role | VARCHAR(16) | NN | enum {LEADER, MEMBER} |
| joined_at | TIMESTAMP(6) | NN | UTC. 크루장 승계 = 최선임(joined_at 최소) |
| status | VARCHAR(16) | NN, default 'ACTIVE' | enum {ACTIVE, WITHDRAWN} |

인덱스: `UQ(crew_id, user_id)`(중복 가입 방지), `IDX(user_id)`, `IDX(crew_id, joined_at)`(승계 조회). 
불변식 주의: "크루당 role=LEADER인 ACTIVE 멤버 정확히 1명"은 **DB로 완전 강제 불가**(부분 유니크 인덱스 한계) → 코드 불변식(§4).

### 2.5 invite_code
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| code | VARCHAR(16) | PK | 자연키. 초대코드 문자열 |
| crew_id | BIGINT | NN, FK→crew(id) | |
| expires_at | TIMESTAMP(6) | NN | UTC. 만료 판정 UTC |
| max_uses | INT | NN | |
| used_count | INT | NN, default 0 | `used_count <= max_uses` 코드 불변식 |

인덱스: `IDX(crew_id)`.

### 2.6 course
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| crew_id | BIGINT | NN, FK→crew(id) | |
| name | VARCHAR(50) | NN | |
| route_polyline | LONGTEXT | NN | 인코딩 폴리라인(경로). **발행 후 불변** — 코드 불변식 |
| distance_m | INT | NN | 코스 거리(m). 완주 판정 기준값 |
| start_lat | DOUBLE | NN | |
| start_lng | DOUBLE | NN | |
| finish_lat | DOUBLE | NN | 도착점 반경 판정 기준 |
| finish_lng | DOUBLE | NN | |
| created_by | BIGINT | NN, FK→user(id) | |
| created_at | TIMESTAMP(6) | NN | UTC (스킬 표에 없으나 감사·정렬용 — 제안) |

인덱스: `IDX(crew_id)`.

### 2.7 race_session
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| crew_id | BIGINT | NN, FK→crew(id) | |
| course_id | BIGINT | NN, FK→course(id) | |
| scheduled_at | TIMESTAMP(6) | **NN** | UTC. 스킬 주석 명시 NOT NULL |
| upload_deadline | TIMESTAMP(6) | **NN** | UTC. "예정+12h"는 앱레이어 기본값, 컬럼은 NN |
| status | VARCHAR(16) | NN, default 'DRAFT' | enum {DRAFT, OPEN, RUNNING, FINALIZING, COMPLETED, CANCELLED} |
| replay_notified_at | TIMESTAMP(6) | NULL | UTC. FCM 세션당 1회 멱등 기록 |

인덱스: `IDX(crew_id, scheduled_at)`, `IDX(status)`, `IDX(course_id)`.

### 2.8 participation
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| session_id | BIGINT | NN, FK→race_session(id) | |
| user_id | BIGINT | NN, FK→user(id) | |
| status | VARCHAR(16) | NN, default 'REGISTERED' | enum {REGISTERED, STARTED, FINISHED, DNF, DNS, WITHDRAWN} |

인덱스: `UQ(session_id, user_id)`, `IDX(user_id)`.

### 2.9 track_record
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| session_id | BIGINT | NN, FK→race_session(id) | |
| user_id | BIGINT | NN, FK→user(id) | |
| started_at | TIMESTAMP(6) | NN | UTC. 각자 시작 버튼 시각 |
| finished_at | TIMESTAMP(6) | NULL | UTC. **서버 확정**(도착점 반경 최초 진입). 미확정 시 NULL |
| total_distance_m | INT | NULL | **정제 후** 좌표로 계산. 미정제 시 NULL |
| total_time_s | INT | NULL | 그로스 타임. 미확정 시 NULL |

인덱스: `UQ(session_id, user_id)`(1인 1레코드), `IDX(user_id)`.

### 2.10 track_payload (1:1 분리)
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| track_record_id | BIGINT | **PK, FK→track_record(id)** | PK=FK, 1:1. 별도 엔티티·리포지토리 |
| raw_payload | LONGTEXT | NN | 원시 트랙(JSON/JSONL). 탈퇴 시 삭제 |
| refined_payload | LONGTEXT | NULL | 정제 트랙. 재정제로 갱신 가능 |

연관 없음(스킬: `@OneToOne` 금지). 조회는 record만, payload는 리플레이/재정제 경로 한정. FK `ON DELETE CASCADE` 허용(record 삭제 시 payload 동반 — payload는 record 종속 파생물).

### 2.11 race_result
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| session_id | BIGINT | NN, UQ, FK→race_session(id) | 세션당 1결과 |
| finalized_at | TIMESTAMP(6) | NN | UTC |

인덱스: `UQ(session_id)`.

### 2.12 rank_entry
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| result_id | BIGINT | NN, FK→race_result(id) | |
| user_id | BIGINT | NN, FK→user(id) | 탈퇴해도 **행 보존**(익명 표시) |
| rank | INT | NULL | 완주자만. 동률 공동순위(1,1,3). DNF/DNS는 NULL. **MySQL 8.0.2+ 예약어 — DDL 백틱 필수, JPA `@Column(name=...)` 인용 주의(R-003)** |
| record_time_s | INT | NULL | DNF/DNS는 NULL |
| is_pb | BOOLEAN | NN, default false | 유저×코스 기준 |

인덱스: `IDX(result_id)`, `IDX(user_id)`. FK `ON DELETE RESTRICT`(탈퇴 시 삭제 아니라 익명화 — CASCADE 절대 금지).

### 2.13 replay_snapshot
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| session_id | BIGINT | NN, FK→race_session(id) | |
| schema_version | INT | NN | 뷰어 호환 판정. 스킬 필수 컬럼 |
| status | VARCHAR(16) | NN | enum {GENERATING, READY, FAILED} |
| payload | LONGTEXT | NULL | 사전계산 스냅샷(추월지점 포함). GENERATING 중 NULL |
| created_at | TIMESTAMP(6) | NN | UTC |

인덱스: `IDX(session_id)`. 재생성 멱등 — 같은 session_id에 복수 행 허용(과거 FAILED 관측 위해). 최신은 created_at max.

### 2.14 reward_plan / reward_item
reward_plan:
| 컬럼 | 타입 | 제약 |
|---|---|---|
| id | BIGINT | PK, AUTO_INC |
| session_id | BIGINT | NN, UQ, FK→race_session(id) |

reward_item:
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | 스킬 표엔 미표기, PK 필요 — 제안 |
| plan_id | BIGINT | NN, FK→reward_plan(id) | |
| rank | INT | NN | 대상 순위. **MySQL 8.0.2+ 예약어 — DDL 백틱 필수, JPA `@Column(name=...)` 인용 주의(R-003)** |
| description | VARCHAR(255) | NN | 보상 내용 |

인덱스: reward_item `IDX(plan_id)`, reward_plan `UQ(session_id)`.

### 2.15 reward_grant
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| id | BIGINT | PK, AUTO_INC | |
| session_id | BIGINT | NN, FK→race_session(id) | |
| user_id | BIGINT | NN, FK→user(id) | |
| item_desc | VARCHAR(255) | NN | 지급 시점 스냅샷(plan 변경 무관하게 장부 보존) |
| status | VARCHAR(16) | NN, default 'PENDING' | enum {PENDING, SENT, CONFIRMED} |
| sent_at | TIMESTAMP(6) | NULL | UTC. 스킬 "누락주의 컬럼" |

인덱스: `IDX(session_id)`, `IDX(user_id)`.

### 2.16 app_min_version
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| platform | VARCHAR(16) | PK | 자연키. enum {ANDROID, IOS} |
| min_version | VARCHAR(20) | NN | semver 문자열 |
| updated_at | TIMESTAMP(6) | NN | UTC |

인덱스: PK만.

> **backend-dev 이관 노트**: 위를 그대로 `V1__init.sql`로. enum은 애플리케이션 검증(CHECK 제약 대신 `@Enumerated(STRING)` + 값 집합)에 맡긴다 — MySQL CHECK는 버전별 무시 이력이 있어 신뢰 근거로 삼지 않는다(코드 불변식 §4로 커버). 시각 컬럼 `TIMESTAMP(6)`은 마이크로초 보존(트랙 포인트 시각 정밀도 대비).

---

## 3. 클라이언트 3인터페이스 + 순수 Dart 코어 (A-C1~C5)

### 3.1 재배치 매핑 (스파이크 → 인터페이스 뒤)

현재 `lib/spike/`는 검증 코드다. 배치 A는 **인터페이스를 정의**하고 스파이크 로직이 그 뒤로 어떻게 옮겨갈지 경계를 확정한다(실 이전은 A-C 구현 시점).

| 현재 스파이크 위치 | 재배치 대상 | 인터페이스/코어 |
|---|---|---|
| `tracking_task_handler.dart`의 `Geolocator.getPositionStream`·`onStart/onDestroy`·Position→TrackPoint 변환 | `lib/platform/location/android_foreground_tracker.dart`(구현체) | `LocationTracker`(인터페이스) |
| `spike_screen.dart` `_ensurePermissions()`(위치/알림/배터리최적화 권한) | `lib/platform/permission/`(구현체) | `PermissionService` |
| `FlutterForegroundTask.init`/`updateService`/상시 알림 | `lib/platform/notification/`(구현체) | `NotificationService` |
| `FlutterForegroundTask.sendDataToMain` 상태 핑(count/lastTs) | 코어 스트림으로 대체 | `LocationTracker.points`/`state` |
| `core/tracking/track_point.dart` | 이미 순수 Dart — **그대로 유지** | `core/tracking` |
| `core/tracking/track_store.dart` | `core/storage`로 승격·일반화(세션 파일명 규칙 일반화, 업로드 큐 분리) | `core/storage` (A-C4 ②) |

`flutter_foreground_task`/`geolocator`/`path_provider` import는 **전부 `lib/platform/` 구현체 안으로만** 이동. `core/`는 import 0건(A-C5 lint로 강제).

### 3.2 `LocationTracker` (격리 ①, 순수 Dart 추상)

```dart
enum TrackerState { ready, running, paused, stopped }

abstract interface class LocationTracker {
  /// 현재 상태(동기 조회). 초기값 ready.
  TrackerState get state;

  /// 상태 변화 스트림(브로드캐스트). UI/상위 레이어 구독용.
  Stream<TrackerState> get stateChanges;

  /// 수신 즉시 방출되는 GPS 포인트 스트림(브로드캐스트).
  /// GPS 시각(TrackPoint.timestamp) 우선. 구현체가 로컬 append 후 방출.
  Stream<TrackPoint> get points;

  /// 트래킹 시작. ready/stopped → running. 권한은 사전 보장 전제
  /// (PermissionService로 확인 후 호출). 재호출(running 중) 무시.
  Future<void> start();

  /// 일시정지. running → paused. 그로스 타임 원칙상 기록 시간은 계속 흐름
  /// (자동 일시정지 아님) — pause는 배터리/샘플링 완화용 훅이지 기록 정지 아님.
  Future<void> pause();

  /// 재개. paused → running.
  Future<void> resume();

  /// 종료. → stopped. 트래킹 중단만 — 도착점 확정·기록 절단은 서버 소관.
  Future<void> stop();

  /// 리소스 해제.
  Future<void> dispose();
}
```

> 계획 AC는 `start/stop/pause`만 명시했으나 `pause` 존재 시 `resume` 없이는 상태머신이 닫히지 않음 → `resume` 추가(미규정 보완, §5). `paused` 상태의 의미는 "샘플링 완화"이지 기록 정지 아님(그로스 타임 불변식 보존).

### 3.3 `NotificationService` (격리 ②)

```dart
abstract interface class NotificationService {
  /// 트래킹 상시 알림 시작(Android=포그라운드 서비스 알림, iOS=후일 로컬 알림).
  Future<void> startTrackingNotification({required String title, required String body});

  /// 상시 알림 내용 갱신(포인트 수·정확도 등). 저빈도 호출 권장.
  Future<void> updateTrackingNotification({required String title, required String body});

  /// 상시 알림 종료.
  Future<void> stopTrackingNotification();
}
```

> 플랫폼 종속 세부(channelId, importance)는 계약/코어에 노출 금지 — 구현체 내부 상수. 서버 무관.

### 3.4 `PermissionService` (격리 ③)

```dart
enum PermissionStatus { granted, denied, permanentlyDenied, restricted }

class TrackingPermissions {
  const TrackingPermissions({required this.location, required this.notification});
  final PermissionStatus location;
  final PermissionStatus notification;
  bool get canTrack => location == PermissionStatus.granted;
}

abstract interface class PermissionService {
  /// 현재 권한 상태 조회(요청 없음).
  Future<TrackingPermissions> check();

  /// 위치 권한 요청. 포그라운드 서비스 방식이므로 "앱 사용 중"이면 충분
  /// (ACCESS_BACKGROUND_LOCATION 요청 금지 — Play 심사 우회가 설계 의도).
  Future<PermissionStatus> requestLocation();

  /// 알림 권한 요청(Android 13+ / iOS). 상시 기록 알림의 조건.
  Future<PermissionStatus> requestNotification();

  /// 배터리 최적화 예외 요청(삼성 앱 자동 종료 대응). 미지원 플랫폼은 no-op.
  Future<void> requestIgnoreBatteryOptimization();

  /// OS 위치 서비스(GPS) 켜짐 여부.
  Future<bool> isLocationServiceEnabled();
}
```

> `requestIgnoreBatteryOptimization`·`isLocationServiceEnabled`는 스파이크 `_ensurePermissions()`에 실재하던 단계 → 인터페이스로 승격(누락 시 스파이크 검증 항목이 사라짐). iOS는 no-op/true로 무해.

### 3.5 순수 Dart 코어 4모듈 (A-C4)

`lib/core/` — 플랫폼 패키지 import 0건(정의). 각 모듈 유닛 테스트 존재.

| 모듈 | 위치 | 책임 | 스파이크 승계 |
|---|---|---|---|
| ① 트래킹 버퍼링 | `core/tracking/` | 메모리 버퍼 + 주기 flush 판단, 적응형 샘플링 간격 판단(주행 3~5s/정지 10s/재개 복귀) — **순수 함수** | `TrackPoint` 유지, 샘플링 판단 신규 |
| ② 로컬 저장 | `core/storage/` | 로컬 우선 append, 세션 파일 관리, 업로드 성공 전 삭제 금지 | `TrackStore` 승격·일반화 |
| ③ 업로드 재시도 | `core/upload/` | 지수 백오프 재시도 정책(순수: attempt→delay), 업로드 큐(로컬 저장, 재시작 시 재개) | 신규 |
| ④ 폴리라인 인코딩 | `core/tracking/` 또는 `core/geo/` | 좌표열↔인코딩 폴리라인(계약 전송형식) — **순수 함수** | 신규 |

**순수 함수 경계(골든 대상)**: 적응형 샘플링 판단, 백오프 지연 계산, 폴리라인 인코딩은 IO·시계·랜덤 금지. 시각이 필요하면 인자로 주입(`now` 파라미터).

---

## 4. 불변식 체크리스트 (배치 A 해당분)

### 4.1 스키마 제약으로 지켜지는 것 (DB가 강제)
- U-1 `kakao_id` 활성 중복가입 방지 → `UQ(kakao_id)`.
- C-1 크루장 컬럼 존재 강제(1명의 "존재") → `crew.leader_id NN`.
- CM-1 동일 크루 중복가입 방지 → `UQ(crew_member.crew_id, user_id)`.
- P-1 세션당 유저 1참가 → `UQ(participation.session_id, user_id)`.
- T-1 세션당 유저 1트랙레코드 → `UQ(track_record.session_id, user_id)`.
- T-2 track_record:payload 1:1 → `track_payload.PK = FK(track_record_id)`.
- RS-1 세션당 1결과 → `UQ(race_result.session_id)`.
- RS-2 `scheduled_at`·`upload_deadline` 필수 → `NN`.
- 시각 UTC 저장 → 컬럼 타입 통일 + JVM/DB 타임존 UTC 고정(§2 규약).
- enum 문자열 저장 → 전 enum `VARCHAR` + `@Enumerated(STRING)`.

### 4.2 코드로 지켜야 하는 것 (DB로 강제 불가 → 애플리케이션/도메인 불변식)
- **C-2 크루장 정확히 1명(ACTIVE 중 role=LEADER 1행)** — 부분 유니크 한계로 DB 불가. 도메인 서비스에서 승계·이양 트랜잭션으로 보장. (배치 B 구현, 배치 A는 스키마만)
- **C-3 크루장 탈퇴 → 최선임(joined_at 최소) 자동 승계**, 마지막 1인 → `crew.status=CLOSED`.
- **IC-1 `used_count <= max_uses`, `expires_at` 미경과일 때만 참가** — 참가 유스케이스에서 검증.
- **CO-1 발행(세션에서 사용)된 course 불변** — 수정 요청은 새 course 생성으로 반려. DB는 UPDATE 막지 않음 → 애플리케이션 가드.
- **U-2 탈퇴 익명화 순서**(nickname 덮어쓰기 + kakao_id NULL + device_token 삭제 + track_payload 삭제 + rank_entry/replay 익명 보존) — 트랜잭션 절차. DB CASCADE로 rank_entry를 지우면 불변식 위반 → FK `RESTRICT` 유지가 방어선.
- **RS-3 상태 전이**(DRAFT→OPEN→RUNNING→FINALIZING→COMPLETED/CANCELLED만) — 상태머신 코드.
- **RK-1 동률 공동순위(1,1,3), DNF/DNS 순위 미부여** — RankingPolicy 순수 함수.
- **RP-1 FCM 세션당 1회 멱등** — `replay_notified_at` 확인·기록.
- **클라 C-4 `core/` 플랫폼 import 0건** — analysis_options lint(A-C5).
- **클라 C-5 로컬 데이터 업로드 성공 전 삭제 금지** — 코드 리뷰 + 저장 모듈 API 설계(삭제 메서드에 업로드확인 게이트).

---

## 5. 미규정 항목 (임의 확정 안 함 — 제안 표기)

- **M-1 (인터페이스)** 계획 AC는 `LocationTracker.pause`만 명시 → 상태머신 폐합 위해 **`resume` 추가 제안**. paused 의미="샘플링 완화, 기록시간 유지"(그로스 타임 불변식과 정합).
- **M-2 (스키마)** `course.created_at`, `reward_item.id`는 스킬 표에 없음 → 감사·정렬·PK 필요로 **추가 제안**.
- **M-3 (스키마)** `device_token.UQ(fcm_token)` 중복 등록 방지 → **제안**(계획서 미규정).
- **M-4 (nickname 익명화 형식)** "탈퇴한 러너" 문자열 그대로 저장 vs 마스킹(`탈퇴한 러너#{id}`) → 리플레이 다인 구분 필요 시 후자. **제안: `탈퇴한 러너` 고정 + rank_entry는 user_id로 구분되므로 표시만 익명**. 배치 B User 구현 시 확정.
- **M-5 (페이지네이션 방식)** cursor vs offset → 크루/세션 목록 규모 작음. **제안: offset+size 단순 방식**(conventions.md 반영), 히스토리 대량화 시 cursor 승격.
- **M-6 (인증 상세)** 자체 토큰(JWT) 형식·만료·갱신은 **배치 B 확정** — conventions.md엔 헤더 방식(`Authorization: Bearer`)과 401 규약만 선반영.
- **M-7 (app_min_version 소속 패키지)** `common` vs 별도 `platform` — **제안: common**(도메인 아님). 오케스트레이터 이견 시 조정.

> O-1(합류/보상 알림 처리), O-2(클라 스택 go_router/Riverpod/dio), O-3(MySQL8/Flyway/Java25)는 planner 열린 질문 — 배치 A 계약·스키마엔 영향 없음(참고만).

---

## 6. 계약 파일 (docs/contracts/) — 이번 배치 산출

- `conventions.md` — 공통 규약(base path, snake_case, UTC, 오류형식, 인증헤더, 페이지네이션)
- `app-version.md` — GET /app-version (인증 불요)
- `crew-api.md` — 크루 생성/내 크루 목록/상세/초대코드 생성/코드 참가
- `session-api.md` — 세션 생성/목록/상세(참가자 상태), RaceStatus·Participation enum 집합

전부 v0.1 · 2026-07-04 · "계약 우선 초안(서버 구현 없어도 됨)" 주석.
