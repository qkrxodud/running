# QA 8차 보고 — M3 (리플레이 파이프라인 + 뷰어) 3자 대조

> 작성: qa · 2026-07-05 · 기준: `73_backend_report_M3A.md`·`74_flutter_report_M3B.md`·`72_analyst_design_M3A.md`, `docs/contracts/replay-api.md` v0.1 · `conventions.md` v0.1.4(§10 딥링크)
> 실행: `./gradlew build` exit 0 (199 tests, 실 MySQL Testcontainers — M3 통합테스트 포함) · `flutter test` 243 통과. 라이브 왕복은 73 곡선의 자동 통합테스트로 갈음(수동 bootRun 미실행 — §5).

## 판정: PASS — 차단 0 / 경고 0 / 참고 4

replay-api 경계면 필드·enum·status·오류코드 3자 일치. RP-1~RP-15 불변식 코드 확인·통합테스트 실증. 딥링크 3자 정합. 조회 로깅 M4 측정 가능. 신규 차단/경고 발견 0.

---

## 1. 3자 대조 (계약 ↔ 서버 ↔ 뷰어)

### 1.1 최상위 응답 (replay-api §1)
| 필드 | 계약 | 서버(`ReplaySnapshotResponse`) | 클라(`ReplaySnapshotResponse`) | 판정 |
|---|---|---|---|---|
| status | ReplayStatus {GENERATING,READY,FAILED} | String(view) | `ReplayStatus` enum+unknown 폴백 | 일치 |
| schema_version | int?(READY만) | Integer | `int?` | 일치 |
| display_names | object?(READY만, `{"<uid>":"nick"}`) | `Map<String,String>`(key=String.valueOf(userId)) | `Map<int,String>?`(int.parse key) | 일치 |
| payload | object?(READY만) | JsonNode(원문 pass-through) | `ReplaySnapshot?` | 일치 |
| GENERATING/FAILED 리터럴 null | 명시적 null | `@JsonInclude(ALWAYS)`로 전역 non_null 오버라이드→명시 null | payload/displayNames/schema null 안전 | 일치 |

### 1.2 payload 스키마 v1 필드 전수
| 경로 | 계약 | 서버(`ReplayGenerationService.buildPayload`) | 클라 | 판정 |
|---|---|---|---|---|
| schema_version·session_id·course{distance_m·route_polyline·start·finish}·duration_ms | v1 | root map 동일 키(:107-117) | `ReplaySnapshot`/`ReplayCourse` | 일치 |
| participants[].{user_id·finish_status·finish_time_ms?·frames·segments} | v1 | `participantJson`(:131) | `ReplayParticipant` | 일치 |
| frames[].{t_ms·lat·lng·cum_dist_m·is_gap} | v1 | `frameJson`(:141) | `ReplayFrame` | 일치 |
| segments[].{seg_index·start_dist_m·end_dist_m·pace_s_per_km·color_bucket} | v1 | `segmentJson`(:151) | `ReplaySegment` | 일치 |
| overtakes[].{at_dist_m·passer_user_id·passed_user_id·t_ms} | v1 | overtake map(:119) | `Overtake` | 일치 |
| **t_ms 상대 int ms** | conventions §9(리플레이 t_ms 상대 정수 ms) | int ms | `int` | 일치 |
| **finish_time_ms DNF 생략(P46-1 유형)** | DNF null | 전역 non_null → Map null값 키 생략 | `int?`(키부재=null) | 일치. `ReplaySnapshotHttpFlowTest:112` DNF `finish_time_ms .has()==false` 실 스택 실증 |

### 1.3 enum wire
- `status` {GENERATING,READY,FAILED} — 서버 String ↔ 클라 `ReplayStatus.wireValues` 일치, unknown 폴백(R-001).
- `finish_status` {FINISHED,DNF} — 서버 `ReplayParticipant.finishStatus`(String, 병합 산출) ↔ 클라 `FinishStatus` 재사용. DNS 부재(트랙 없음). 통합테스트가 DNF 참가자 실증.

### 1.4 버전 게이트 (RP-11 — 크래시 금지 테스트 존재 확인)
- 계약: schema_version > MAX → "앱 업데이트" 렌더 거부(크래시 금지). MAX=1.
- 클라: `kMaxSupportedSnapshotVersion=1`, `isVersionSupported = payload.schemaVersion ≤ MAX`.
- **테스트 존재 확인**: `replay_dtos_test.dart:61-86` — schema_version 2 payload → `res.payload isNotNull`(크래시 없이 파싱) + `isVersionSupported isFalse`(렌더 거부). unknown status → 폴백(크래시 금지). ✅

### 1.5 오류코드
- 계약: 403 FORBIDDEN(비멤버)·404 NOT_FOUND(세션 없음/스냅샷 미생성).
- 서버 `ReplayQueryService.getReplay`: sessionExists→404 · isCrewMember→403 · 스냅샷 미생성→404. 순서·코드 정합. 통합테스트 비멤버 403·미생성 404 실증.

---

## 2. 불변식 (RP-1 ~ RP-15)

| # | 불변식 | 확인 |
|---|---|---|
| RP-1 | payload 격리 5차 — replay 생성만 `ReplaySourcePort`(track_payload native), 조회 어댑터 미주입 | ✅ `ReplayQueryAdapter`는 race_session·crew_member·race_result·user만 조인, **track_payload 미접근**. 순위/결과/히스토리 어댑터 여전히 미주입 |
| RP-2 | Tracking/Ranking 직접 호출 0(이벤트/포트만) | ✅ 네이티브 SQL 포트만. ArchUnit R-1~4 green(73 §1) |
| RP-3 | payload 표시명 미내장(조회 조인·탈퇴 익명) | ✅ payload=user_id만, `ReplayQueryAdapter.displayNames` status=WITHDRAWN→"탈퇴한 러너" |
| RP-7 | GENERATING/FAILED payload NULL, READY만 payload | ✅ saveGenerating(null)→markReady(payload)/markFailed(null) |
| RP-8 | FAILED 관측·재시도(조용한 실패 금지) | ✅ catch→markFailed + `log.error(...cause...)`. admin 재생성 경로 존재 |
| RP-9 | 생성 AFTER_COMMIT @Async·REQUIRES_NEW(확정 tx 분리) | ✅ `ResultFinalizedReplayListener` + `generate` REQUIRES_NEW |
| RP-10 | 재생성 멱등(동일 바이트) | ✅ 순수함수 재계산. 라이브 곡선 동일 3542 bytes(73 §1). 통합테스트 재생성→READY |
| RP-12 | 알림 세션당 1회(replay_notified_at)·재생성 재발송 금지 | ✅ `ReplayNotificationGateAdapter.markNotifiedIfFirst` 원자적 `UPDATE ... WHERE replay_notified_at IS NULL`(updated==1만 발송). `ReplaySnapshotHttpFlowTest` 재생성 후 알림 미재발 실증 |
| RP-13 | 2MiB 상한 초과 → FAILED | ✅ `buildPayload` bytes>maxBytes(2097152 외부화)→throw→markFailed |
| RP-14 | 조회 로깅 존재(user_id만·익명) | ✅ `ReplayQueryService.logView` |
| RP-15 | 딥링크 키=sessionId(재생성 내성) | ✅ `runningcrew://replay/{sessionId}` |

---

## 3. 딥링크 정합 (conventions §10 — 3자)
| 알림 | 서버 FCM data.deep_link | conventions §10 | 클라 라우트 | 판정 |
|---|---|---|---|---|
| REPLAY_READY | `runningcrew://replay/{sessionId}` (`ReplayGenerationService:177`) | `runningcrew://replay/{sessionId}`→`/sessions/:id/replay` | 라우트 `/sessions/:sessionId/replay` + 딥링크 매핑(74 §진입) | 일치 |
| SESSION_REMINDER | `runningcrew://session/{sessionId}` (`SessionReminderService:44`) | `runningcrew://session/{sessionId}`→`/sessions/:id` | `/sessions/:id` | 일치 |
- `data.deep_link` 단일 필드·키=sessionId(snapshotId 아님). 실 FCM 수신은 M3-C Firebase 게이트 뒤 — 규약·경로·스킴 3자 확정.

---

## 4. 조회 로깅 (A9 — M4 성공기준 측정 가능성)
- `replay_viewed session_id·user_id·viewed_at·finalized_at·viewed_within_24h` 구조화 로그(`ReplayQueryService.logView`). within24h = (viewed_at − finalized_at ≤ 24h).
- **측정 가능성 판정: 가능**. session별 (참가자 수, 24h내 고유 조회 user_id 수) 집계로 "레이스 후 24h 조회율 80%"(계획서 §8) 산출 가능. user_id만(닉네임·위치 없음 — RP-14 익명 파생).
- **참고**: MVP는 구조화 로그(전용 테이블 아님 — 설계 §5 제안 정합). M4 확인루틴이 로그 집계 전제 → 로그 수집·집계 파이프라인이 M4에 실제로 갖춰지는지는 M4 스코프에서 재확인 필요(현재는 emit 존재까지 확인).

---

## 5. 실행 검증
- `./gradlew build` exit 0 — 199 tests, 실 MySQL Testcontainers. `ReplaySnapshotHttpFlowTest`가 라이브 곡선(자동 READY·표시명 조인·payload 스키마 v1·is_gap·overtakes·**DNF finish_time_ms 생략**·재생성 멱등·알림 1회 미재발·admin 미인증 403·비멤버 403·미생성 404)을 **바이트 단위 assert**로 자동화. `SessionReminderHttpFlowTest`(리마인더 멱등·딥링크).
- `flutter test` 243 통과.
- **라이브 왕복**: 별도 수동 bootRun+curl 미실행. 73 §1 라이브 곡선이 위 통합테스트로 자동화·영구 회귀선화됨 — 갈음. sandbox 무접촉. docker 가용.

---

## 6. 이월

- **참고 P26-2 / A10 공유 픽스처**: `docs/contracts/fixtures/replay_snapshot_v1.json` 존재하나 flutter-dev 수기 작성본(74 §3.1). 서버 생성 실 바이트 ↔ 뷰어 파싱 교차 CI(`SharedContractFixtureTest` 확장)는 미배선 — test-engineer 소관. 이번 회차에서 서버 생성 payload 스키마 ↔ 클라 파싱 필드셋 실측 일치는 확인(통합테스트 + 3자 대조), 단일 공유 바이트 픽스처 CI는 종결 아님.
- **참고 조회 로깅 M4 파이프라인**: emit(구조화 로그)은 존재. 로그→집계→24h 조회율 산출 루틴은 M4 스코프에서 실측 재확인(측정 데이터소스 준비 완료, 소비 루틴 미배선).
- **참고 추월 노이즈 필터**: v1 부호반전 전량 기록(설계 §9). GPS 노이즈성 미세 재역전이 다수 overtake를 만들 수 있음 — 실주행 픽스처 관찰 후 최소 간격 필터 도입 여부 결정(제안값 진행, 범위 밖).
- **참고 프레임드랍 실측(자동 불가)**: 60fps·10명×600pt 동시 재생 드랍은 실기기 DevTools Performance overlay 수동 절차(74 §4). 정적/동적 painter 분리·RepaintBoundary로 설계상 완화. 실기기·네이버 지도 SDK·딥링크 실수신은 게이트 뒤.
- **이월(범위 밖)**: 실 FCM 발송·클라 수신(M3-C Firebase), RewardGrant 생성(M3-C Reward), 네이버 지도 타일 렌더.
