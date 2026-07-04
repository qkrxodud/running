# 04 — Flutter 개발자 보고 (배치 A: 클라이언트 골조)

> 작성: flutter-dev · 날짜: 2026-07-04 · 대상: 배치 A A-C1~A-C6 (`app/`)
> 기준: `01_planner_plan.md`(배치 A), `02_analyst_design.md`(§3 시그니처가 진실), `docs/contracts/conventions.md`, flutter-client·domain-model 스킬

## 0. 완료 기준 결과

| 항목 | 결과 |
|---|---|
| `flutter analyze` | **No issues found** (0 이슈) |
| `flutter test` | **All tests passed** (30개: 기존 5 + 신규 25) |
| `flutter build apk --debug` | **성공** — 스파이크 화면 동작 보존 확인 |

의존성 추가: `go_router`, `flutter_riverpod`, `dio` (`flutter pub add`).

---

## 1. 구현 내역

### 1.1 격리 3인터페이스 (순수 Dart, 설계 §3.2~3.4 시그니처 그대로)
- `lib/platform/location/location_tracker.dart` — `LocationTracker` + `TrackerState{ready,running,paused,stopped}`. **resume 포함**(설계 확정, 상태머신 폐합).
- `lib/platform/notification/notification_service.dart` — `NotificationService`.
- `lib/platform/permission/permission_service.dart` — `PermissionService` + `PermissionStatus` + `TrackingPermissions`.
- 이 3파일에는 플랫폼 패키지 import 없음(코어 레이어가 안전하게 의존 가능).

### 1.2 Android 구현체 (플랫폼 import 격리) — 설계 §3.1 매핑표 반영
- `lib/platform/location/android_foreground_tracker.dart` — `AndroidForegroundTracker`. 스파이크의 geolocator 스트림 + flutter_foreground_task 서비스 로직을 인터페이스 뒤로 재배치. 서비스 isolate 핸들러(`_AndroidTrackerTaskHandler`)가 `TrackPoint.toJson()` 을 main isolate 로 전송 → 트래커가 `points` 스트림으로 방출.
- `lib/platform/permission/android_permission_service.dart` — 스파이크 `_ensurePermissions()`(위치/알림/배터리최적화/GPS)를 승격. geolocator·flutter_foreground_task 권한 API를 `PermissionStatus` 로 정규화.
- `lib/platform/notification/android_notification_service.dart` — 상시 알림 내용 갱신 위임. **결합 주의**: Android 에서 상시 알림 lifecycle 은 Foreground Service 소유(트래커가 start/stop), 이 서비스는 내용 갱신 담당.
- **`geolocator`/`flutter_foreground_task`/`path_provider` import 는 전부 `lib/platform/` 구현체 + 기존 `lib/spike/` 내부에만 존재** — `lib/core/` 0건(테스트로 강제, §3).

### 1.3 순수 Dart 코어 확장 (`lib/core/`, 플랫폼 import 0건)
- `core/geo/lat_lng.dart` — 순수 좌표 VO.
- `core/geo/polyline_codec.dart` — **Google Encoded Polyline(1e-5) 인코딩·디코딩 구현**. 계약 전송형식(인코딩 폴리라인)의 좌표열 담당.
- `core/tracking/sampling_policy.dart` — **적응형 샘플링 간격 판단**(주행 4s/정지 지속 10s/재개 복귀). 시각·IO 없음(정지 지속시간·속도 인자 주입).
- `core/tracking/track_buffer.dart` — **버퍼링 정책**(개수·age 임계 flush 판단, now 주입). 실제 파일 쓰기는 저장 모듈 소관.
- `core/upload/backoff_policy.dart` — **지수 백오프 지연 계산**(base·multiplier·maxDelay·maxAttempts, 랜덤 없음).
- `core/upload/upload_queue.dart` — **업로드 재시도 큐 골격**(상태 전이 순수 로직, HTTP 없음 → 실 전송은 배치 B). **C-5 보장**: 성공 확정 전 어떤 전이도 작업 제거 안 함, maxAttempts 소진도 `failed` 보존.
- 기존 `core/tracking/track_point.dart`·`core/tracking/track_store.dart` 는 **그대로 유지**(요청대로 이동 안 함 — 기존 테스트·스파이크 import 보존).

### 1.4 스캐폴드 정리
- `lib/app/app_theme.dart` — 1a 라임 디자인 토큰(ink/lime/bg/muted/accent 4색) + `AppTheme.light()`.
- `lib/app/router.dart` — go_router. `/` 홈 셸, `/spike` 스파이크(실기기 검증 대기 → 보존).
- `lib/features/home/home_screen.dart` — 빈 홈 셸(카운터 제거). 개발용 스파이크 진입 버튼.
- `lib/main.dart` — `ProviderScope` + `MaterialApp.router` 재구성. `FlutterForegroundTask.initCommunicationPort()` 는 composition root 로 유지(스파이크·트래커 sendDataToMain 수신 전제).

---

## 2. 사용한 계약

- `docs/contracts/conventions.md` — 코어 구조에 반영: snake_case JSON(`TrackPoint.toJson`), UTC 시각(폴리라인·버퍼·백오프의 시각은 전부 주입식 UTC 전제). **이번 배치에 서버 HTTP 호출 구현 없음**(재시도 큐는 순수 골격, 실 전송·DTO 파싱은 배치 B). enum 미지값 폴백 규약은 배치 B DTO 파싱에서 적용 예정.

---

## 3. 실기기 수동 테스트 절차 (자동 검증 불가 항목)

백그라운드 트래킹은 CI 자동 검증 불가 — 다음 절차는 유지된다(스파이크 화면 보존).

1. 앱 실행 → 홈에서 **"트래킹 스파이크 (개발용)"** 진입(신규: `/spike` 라우트. 기존엔 앱 시작 즉시 스파이크였음 — **진입 경로만 1단계 추가, 기능 동일**).
2. "기록 시작" → 권한 허용(위치=앱 사용 중, 알림, 배터리 최적화 예외) → 화면 끄고 주머니 → **1시간 이상** 유지 → "기록 중지".
3. 기대: 4초 간격 · 시간당 ~900포인트 · 유실 없음. 저장 파일 경로·포인트 수 화면 확인.

**변경점**: 앱 진입 시 홈 셸이 먼저 뜨므로, 스파이크 도달에 홈의 버튼 탭 1회가 추가됨. 트래킹 로직·권한 플로우·저장 규약은 스파이크 코드 그대로(무수정).

**AndroidForegroundTracker 실기기 검증(신규, 미수행)**: `AndroidForegroundTracker` 는 스파이크와 동일 메커니즘이나 별도 isolate 콜백(`androidTrackerCallback`)을 쓰는 신규 경로다. 실 배선(홈→트래커 사용)은 배치 B이므로, 그 시점에 스파이크와 동일한 1시간 유실 테스트를 트래커 경로로 1회 재수행 필요.

---

## 4. test-engineer 에게 넘기는 순수 Dart 함수/클래스 (골든 테스트 대상)

IO·시계·랜덤 없음(시각은 인자 주입). 실주행 픽스처 기반 골든 테스트 부착 가능.

| # | 대상 | 위치 | 핵심 시그니처 |
|---|---|---|---|
| 1 | `PolylineCodec.encode` / `.decode` | `core/geo/polyline_codec.dart` | `String encode(List<LatLng>)`, `List<LatLng> decode(String)` |
| 2 | `SamplingPolicy.intervalFor` | `core/tracking/sampling_policy.dart` | `Duration intervalFor({required double speedMps, required Duration stationaryFor})` |
| 3 | `TrackBuffer.shouldFlush` / `.drain` | `core/tracking/track_buffer.dart` | `bool shouldFlush(DateTime now)`, `List<TrackPoint> drain()` (add 는 `{required DateTime now}`) |
| 4 | `BackoffPolicy.delayForAttempt` / `.shouldRetry` | `core/upload/backoff_policy.dart` | `Duration delayForAttempt(int attempt)`, `bool shouldRetry(int attempt)` |
| 5 | `UploadQueue` 상태 전이 | `core/upload/upload_queue.dart` | `dueTasks(now)`, `markInFlight/markFailed(id,now,policy)/markSucceeded/purgeSucceeded` |

각 대상에 최소 유닛 테스트를 이미 첨부(`test/core/...`) — test-engineer 는 실주행 픽스처·경계값 확장을 담당.

---

## 5. qa 가 검증해야 할 경계면

- **C-4 (플랫폼 격리)**: `lib/core/` 플랫폼 import 0건. `test/core/no_platform_imports_test.dart` 가 **allowlist 방식**으로 강제 — `dart:*`+상대경로 외 모든 `package:` import 를 위반 처리(analysis_options 커스텀 lint 대체, R-002 대응). 신규 코어 모듈 추가 시 이 경계 유지 확인.
- **C-5 (로컬 우선)**: `UploadQueue` 는 성공 확정(`markSucceeded`) + `purgeSucceeded` 전엔 작업 미제거. maxAttempts 소진도 `failed` 로 **보존**(테스트 명시). 배치 B 실 전송 배선 시 이 게이트 우회 여부 리뷰.
- **인터페이스 시그니처 준수**: 3인터페이스가 설계 §3.2~3.4 와 일치(특히 `LocationTracker.resume` 포함). Android 구현체가 인터페이스를 그대로 구현.
- **스파이크 보존**: 진입 경로만 홈→`/spike` 로 1단계 추가, 트래킹 기능 무변경.

---

## 6. 미해결·이관 사항

- 폴리라인 코어와 실 트래킹 파이프라인(버퍼→저장→업로드) **배선은 배치 B**(설계 §0 "미룸"과 일치). 이번 배치는 경계·순수 로직만 확정.
- `AndroidNotificationService` 의 상시 알림 lifecycle 이 Foreground Service 에 결합(Android 본질) — iOS 확장 시 로컬 알림 구현으로 분리 예정. 인터페이스는 이미 그 분리를 수용하는 형태.
- `TrackStore` 의 `core/storage` 승격(설계 §3.5 제안)은 기존 테스트·스파이크 import 보존을 위해 **이번 배치 미수행**(요청: "기존 tracking/ 유지"). 배치 B 실 저장 배선 시 일반화 검토.

---

## 7. 수정 이력

- 2026-07-04 (QA R-002 대응): 플랫폼 격리 가드(`test/core/no_platform_imports_test.dart`)를 denylist → **allowlist** 전환 — `lib/core/` 허용 import 는 `dart:*`+상대경로만, `package:` 전부 실패. 캐너리(`package:dio` 임시 삽입) 검출 검증 후 analyze 0 이슈 / 테스트 30개 전체 통과. `docs/regressions.md` R-002 CLOSED.
