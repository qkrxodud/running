# 47 — Flutter 구현 보고 (M2-B0: 기기 무관 클라이언트 — 상태머신·DTO·업로드 배선)

> 작성: flutter-dev · 2026-07-04 · 기준: `41_planner_plan_M2.md`(§M2-B B0), `42_analyst_design_M2A.md`, `docs/contracts/track-api.md` **v0.1.1**, `conventions.md` v0.1.2
> 완료 기준: `flutter analyze` 0 issues · `flutter test` 177개 전체 통과(신규 +69) · R-002 가드 green
> **범위 준수**: 화면 작업 없음(레이스 진행·결과 화면은 M2-B/C). 코어·데이터 계층만. 실배선(트래커→서버)은 스파이크 게이트 뒤 M2-B 본체.

---

## 1. 구현 모듈

### A. 클라 로컬 상태머신 (순수 Dart) — `lib/core/race/race_state_machine.dart`
- `RaceLocalState`: `READY → RUNNING → FINISHED_LOCAL → UPLOADED` (wire 문자열 보유 — **로컬 영속용, 서버 계약 아님**).
- `RaceLocalSession`(불변 값 객체): `start()`/`finishLocal()`/`markUploaded()` — 각 전이는 선행 상태 검증, 위반 시 `RaceStateTransitionError`.
- **불변식**:
  - FINISHED_LOCAL = 서버 미전송 상태. `markUploaded()`는 **FINISHED_LOCAL 에서만** 호출 가능 → 업로드 성공 확인 전 UPLOADED 전이 불가(RUNNING→UPLOADED 건너뛰기 차단).
  - `retainsLocalData`: RUNNING·FINISHED_LOCAL 은 로컬 데이터 보존 대상(삭제 판단 근거).
- **중복 시작 방지**: `start()`는 READY 에서만 → 동일 세션 재시작 거부. 추가로 `canStartNewRace(current)` — RUNNING/FINISHED_LOCAL 세션이 있으면 새 레이스 거부(단일 활성 레이스 불변식).
- `toJson`/`fromJson` — 강제종료 복구용 직렬화(M2-B 리커버리에서 소비).
- **서버 `ParticipationStatus` 와 별개** 명시(혼동 금지 — 주석·테스트로 박제).

### B. track-api DTO (순수 Dart) — `lib/core/model/track_dtos.dart`
- `TrackUploadRequest`: `TrackUploadRequest.fromPoints(...)`가 **기존 `PolylineCodec.encode`(1e5) 재사용**(신규 core 의존 0 — R-002) + 병렬 배열(timestamps=**epoch millis**, speeds, accuracies, altitudes?) + `client_upload_id`(멱등 키) + `ClientMeta{os, os_version, device_model}` **3키 고정**.
- `TrackRecordResponse`(§1/§2), `RaceResultResponse`·`ResultEntry`·`ResultCourse`(§3).
- **P46-1 (키 부재=null)**: 전 nullable 필드를 `json['x'] as T?` 로 파싱(키 생략·명시 null 모두 null). DNF 응답(finished_at/total_time_s 키 없음)·DNS 항목(rank/record_time_s/total_distance_m/avg_pace 키 없음) 파싱 테스트로 박제.
- **P46-2**: `avg_pace_s_per_km` 필드명 그대로 고정(테스트가 오타 변형을 감지).
- **enum wire + unknown 폴백(R-001)**: `ProcessingStatus{PROCESSED}`, `FinishStatus{FINISHED,DNF}`, `ResultEntryStatus{FINISHED,DNF,DNS}` — 전부 `enum_codec` 경유, 계약 값집합 대조 테스트(`enum_contract_test.dart` + `track_dtos_test.dart`).

### C. 오류 코드 분기 (순수 Dart) — `lib/core/model/track_error.dart`
- `TrackErrorCodes` 상수 + `TrackUploadErrorKind` + `classifyTrackUploadError(ApiException)`.
- **R-007 클라 분기 완료**: **403 FORBIDDEN=`forbiddenNotMember`(비멤버)** / **409 SESSION_STATE_INVALID=`notRegisteredOrBadState`(미등록·상태)** / **409 TRACK_ALREADY_UPLOADED=`alreadyUploaded`(중복)** 를 서로 다른 분류로 배타 매핑. **code 로만 분기, 메시지 매칭 없음**(테스트로 강제). 400계열→payloadInvalid, 413→tooLarge, 404→notFound, statusCode 0→network, 5xx→server.
- `isRetryable`: network·server 만 true(일시적 장애). 4xx 클라 오류·unknown 은 false(자동 재시도 무의미 → 수동 재시도/사용자 조치로 회부).

### D. 업로드 큐 종단 실패 (순수 Dart) — `lib/core/upload/upload_queue.dart`
- `markTerminalFailure(id)` 추가 — 백오프 없이 즉시 `failed`(비재시도 4xx). maxAttempts 소진과 동일하게 **보존만**(제거 안 함 — C-5/R-002). 순수(플랫폼 import 0 유지).

### E. 업로드 리포지토리 배선 (dio) — `lib/data/track_repository.dart`
- `HttpTrackRepository`: `upload`(POST /sessions/{id}/track, 201/200), `myTrack`(GET .../track/me, **404→null**), `result`(GET .../result).
- `result` → `ResultQueryOutcome`(sealed): `ResultReady` | **`ResultPending`**(409 `RESULT_NOT_READY` → 예외 아닌 "전원 완료 대기" 매핑, code 로만 분기).
- `upload` 은 `DioException`→`ApiException`(계약 code) 정규화 후 던짐 → 호출자·코디네이터가 `classifyTrackUploadError` 로 분기. **dio 의존은 data 계층에만**(core 순수 유지 — R-002).

### F. 업로드 코디네이터 (dio↔순수큐 배선) — `lib/data/upload_coordinator.dart`
- 코어 `UploadQueue`·`BackoffPolicy` 를 `TrackRepository`(HTTP)에 연결. `pump(now)` 가 dueTasks 를 순회하며 markInFlight→upload→(성공)markSucceeded / (실패)markFailed|markTerminalFailure.
- **R-002 성공 확인 전 로컬 삭제 금지**: 로컬 삭제 훅 `onSucceeded` 는 **markSucceeded 직후에만** 호출(테스트가 `upload`→`delete-local` 순서 박제). 실패 시 삭제 훅 미호출.
- **멱등 키 보존**: 재시도는 동일 `recordRef` 로 `loadJob` 재호출 → 저장된 동일 `client_upload_id` 재사용(재생성 없음, 테스트로 박제). 키 생성기 `lib/data/upload_id.dart`(uuid v4, `Random` 주입 가능 — 랜덤이라 data 계층).

---

## 2. 테스트 (신규 +69, 전체 177 통과)

| 파일 | 검증 |
|---|---|
| `test/core/race/race_state_machine_test.dart` | 정상 전이·불변식(RUNNING→UPLOADED 차단)·중복시작 방지·직렬화 왕복 |
| `test/core/model/track_dtos_test.dart` | 폴리라인 1e5 왕복·병렬배열 길이 N·epoch millis·**P46-1 키부재=null**(DNF/DNS)·**P46-2 avg_pace 필드명**·enum 폴백 |
| `test/core/model/track_error_test.dart` | **R-007 403/409 배타 분기(code only)**·재시도 정책 |
| `test/core/model/enum_contract_test.dart` | track enum 3종 계약 값집합 대조(R-001) 추가 |
| `test/core/upload/upload_queue_terminal_test.dart` | markTerminalFailure 보존·비재시도 |
| `test/data/track_repository_test.dart` | 403/409/404/RESULT_NOT_READY 매핑·경로·body |
| `test/data/upload_coordinator_test.dart` | 성공시 로컬삭제 순서(R-002)·백오프 재시도·종단·멱등키 재사용 |
| `test/core/no_platform_imports_test.dart` | **R-002 가드 green**(신규 core 파일 순수성 확인) |

---

## 3. qa 3자 대조 대상 목록 (계약 v0.1.1 ↔ 서버 ↔ 클라 DTO)

1. **R-007 완결(잔여 조건)**: track-api §1 평가순서(404→403→409상태→409중복→400/413) — 서버 `TrackUploadMembershipGuardTest`(403 비멤버/409 미등록) vs 클라 `classifyTrackUploadError`(403 forbiddenNotMember / 409 notRegisteredOrBadState / 409 alreadyUploaded). **3자 재검증 요청**.
2. **enum wire 대조**: `finish_status{FINISHED,DNF}`·`processing_status{PROCESSED}`·`result.status{FINISHED,DNF,DNS}` — 서버 응답 실 JSON ↔ 클라 wireValues(공유 픽스처 CI A11 연장 대상).
3. **P46-1 키부재=null**: 서버 전역 NON_NULL 직렬화가 DNF/DNS 시 키를 실제로 생략하는지 vs 클라 nullable 파싱. 서버 실 응답 픽스처로 왕복 검증(A11).
4. **P46-2 avg_pace_s_per_km**: 서버 ResultEntry DTO 필드명 == 클라 파싱 키. 오타 시 클라 null 조용히 반환되므로 **서버 필드명 실 응답 대조 필수**.
5. **폴리라인 1e5 왕복**: 클라 encode ↔ 서버 decode 규약 동일성(tie=half-away-from-zero). 실 좌표열 왕복 픽스처(A11).
6. **멱등**: 동일 `client_upload_id` 재요청 → 서버 200(기존결과) vs 다른 내용 → 409 TRACK_ALREADY_UPLOADED. 클라는 동일 키 재사용만 보장 — 서버 멱등 수용 동작 교차 확인.

---

## 4. M2-B 본체 대기 목록 (스파이크 PASS 관문 뒤 — 이번 범위 밖)

- **트래커 실배선**: `AndroidForegroundTracker` 실구현 + 적응형 샘플링 라이브 → 버퍼 → 로컬저장 → `UploadCoordinator.enqueue`. (본 B0 는 순수 로직·계약만, 실 기기 스트림 미연결.)
- **화면**: 레이스 진행 화면(경과·거리·페이스·지도·종료)·결과 대기 화면(n/m)·순위표(M2-C) — dc.html "1a 라임" 기준.
- **리커버리 실배선**: `RaceLocalSession.fromJson` + 로컬 저장을 강제종료 복구 플로우에 연결(이어가기/그시점 종료).
- **업로드 실패 UI**: `TrackUploadErrorKind` 별 안내 — network/server=자동 재시도 + 수동 재시도 버튼, forbiddenNotMember/notRegistered=사용자 조치 안내, alreadyUploaded=결과 조회 유도.
- **client_meta 실값**: `ClientMeta{os,os_version,device_model}` 를 실제 기기 정보로 채우는 PlatformService(플랫폼 격리 경계 내).
- **온보딩**: 배터리 최적화 예외·권한 플로우(PermissionService).
- **실기기 검증(자동 불가)**: 절전·앱 자동종료·화면 꺼짐 장시간 백그라운드 기록 — 수동 절차(화면 끈 채 1시간 기록 + 업로드 재개 확인).

---

## 5. 계약 준수 자기검증

폴리라인 1e5 기존 코덱 재사용(신규 core 의존 0, R-002) ✅ / epoch millis(§9) ✅ / client_meta 3키 고정(§8) ✅ / P46-1 키부재=null ✅ / P46-2 필드명 ✅ / enum unknown 폴백(R-001) ✅ / 403·409 배타 code 분기(R-007) ✅ / 성공확인 전 로컬삭제 금지(R-002·C-5) ✅ / 멱등키 재사용 ✅ / RESULT_NOT_READY→대기 매핑 ✅ / 로컬 상태머신 서버상태 분리 ✅ / 화면 미구현(범위 준수) ✅.
