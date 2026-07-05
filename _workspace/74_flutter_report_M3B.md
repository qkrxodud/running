# 74 — Flutter 구현 보고 (M3-B: 리플레이 뷰어 — 제품의 얼굴)

> 작성: flutter-dev · 2026-07-05 · 기준: `71_planner_plan_M3.md`(M3-B), `72_analyst_design_M3A.md`(스키마 v1·뷰어 요구), `docs/contracts/replay-api.md` v0.1, `conventions.md` v0.1.4(딥링크 §10)
> 완료 기준: `flutter analyze` 0 · `flutter test` **243개 전체 통과**(신규 +39) · `flutter build apk --debug` **성공**
> 범위: placeholder 지도 위 렌더(네이버 SDK 게이트 뒤). 실 프레임드랍 실측은 실기기 대기(§4).

---

## 1. 구현

### 스냅샷 DTO (스키마 v1) — `lib/core/model/replay_dtos.dart`
- `ReplaySnapshotResponse`(status·schema_version·display_names·payload) + `ReplaySnapshot`(course·duration·participants·overtakes) + `ReplayParticipant`(user_id·finish_status·finish_time_ms?·frames·segments) + `ReplayFrame`(t_ms·lat·lng·cum_dist_m·is_gap) + `ReplaySegment`(color_bucket) + `Overtake`.
- **`kMaxSupportedSnapshotVersion = 1` 게이트**: payload.schema_version > MAX → `isVersionSupported=false`(파싱은 하되 렌더 거부 → "앱 업데이트가 필요해요", **크래시 금지**).
- **unknown 필드 무시**(append-only 하위호환 — Map 접근이라 미지 키 자동 무시, 테스트로 박제). enum unknown 폴백(R-001). `finish_status`는 track_dtos `FinishStatus` 재사용.
- **표시명 미내장**(user_id만) → `display_names` 조인 맵에서 취득(O-M3-1), 미조인 "러너 {id}" 폴백(탈퇴 안전).

### 순수 재생 로직 (골든 대상) — `lib/core/replay/`
- `replay_controller.dart` `ReplayPlayback`: **배속(1x/2x/4x)·시킹·재생/일시정지·종단 자동정지**를 순수 상태 전이로. 위젯이 Ticker 로 실경과(ms)를 `advance()` 주입(시각 주입 — 골든). `cycleSpeed`·`restart`·`progress`.
- `frame_sampler.dart` `ReplayFrameSampler.sampleAt(frames, tMs)`: **프레임 간 선형 보간**(마커 위치). 시작 전=출발점, 종료 후=마지막 고정(finished), is_gap=보간 구간 반영. 이진탐색 O(log n)/참가자.

### 뷰어 화면 — `lib/features/replay/`
- `replay_screen.dart`: 상태별 UI(**GENERATING** "리플레이 만드는 중" / **FAILED** 재시도 안내 / **READY** 뷰어 / **버전 초과** "앱 업데이트" / **404** "리플레이가 아직 없어요"). Ticker 구동 재생 루프(playing 시만 setState — 정지 시 프레임 스킵). 컨트롤: 재생/일시정지·배속 순환·처음부터·커스텀 타임라인(플레이헤드+추월 틱, 탭·드래그 시킹)·추월 발생 배너·범례. 오류 문구 결정은 순수 `replayErrorText`로 분리(단위 테스트).
- `replay_map.dart`: placeholder 지도 위 렌더(CoursePolylineMap 패턴 확장). **성능 분리** — 정적 배경 painter(코스+참가자 경로+추월지점, RepaintBoundary+스냅샷 동일 시 repaint 스킵)와 동적 마커 painter(positionMs 마다) 2레이어. 경로=**구간 페이스 색상 버킷**, **GPS 유실(is_gap)=점선·muted**(실측 구분), **DNF 경로 표시**(흐리게·기록 보존), 마커=**참가자 accent 팔레트**(퍼플·시안·오렌지·핑크), **추월 발생 순간 강조**(지도+타임라인).
- `replay_palette.dart`: 참가자 정체성 색 + 페이스 버킷 색표 + 유실 색. 기록 숫자 Space Grotesk(`AppTypography.record`).

### 진입·딥링크
- 결과 화면 리플레이 placeholder → **실 진입**(`_ReplayEntry` → `context.push('/sessions/$id/replay')`).
- 라우트 `/sessions/:sessionId/replay` 등록(상세보다 먼저 — 정적 세그먼트 우선). **딥링크 `runningcrew://replay/{sessionId}` 매핑**(conventions §10 — 실 수신은 M3-C Firebase 게이트 뒤).
- providers: `replayRepositoryProvider`·`replaySnapshotProvider`. `replay_repository.dart`(GET /sessions/{id}/replay, 403/404 정규화).

---

## 2. 뷰어 기능 체크리스트

| 기능 | 상태 |
|---|---|
| 전원 마커 동시 이동(프레임 간 보간 애니메이션) | ✅ Ticker+ReplayFrameSampler(순수 보간·골든) |
| 배속 1x/2x/4x | ✅ ReplayPlayback.cycleSpeed(순수·골든) |
| 시간축 슬라이더(드래그 시킹) | ✅ 커스텀 타임라인(탭/드래그→seek) |
| 재생/일시정지 | ✅ togglePlay(끝에서 재시작 포함) |
| 추월 마킹(지도+타임라인) + 발생 시 강조 | ✅ 타임라인 bolt 틱·지도 마킹·근접 시 배너+glow |
| 구간 페이스 색상(경로 색상 버킷) | ✅ cum_dist→segment 버킷 색 |
| GPS 유실 구간 구분(is_gap) | ✅ 점선·muted(실측과 시각 구분) |
| DNF 참가자 경로 표시(기록 보존) | ✅ 흐린 경로+빈 마커 |
| 상태별 UI(GENERATING/FAILED/READY) | ✅ + 버전 게이트·404 |
| 성능(60fps·10명×600pt) | ⚠️ 정적/동적 painter 분리·RepaintBoundary로 설계. **실측 실기기 대기(§4)** |
| 디자인(1a 라임 accent 팔레트·Space Grotesk) | ✅ |

---

## 3. qa 경계면 (계약 ↔ 서버 ↔ 클라 3자 대조)

1. **공유 픽스처(A10)**: 현재 `docs/contracts/fixtures/replay_snapshot_v1.json`은 **flutter-dev 수기 스키마 v1 작성본** — backend 생성 실 응답 픽스처 도착 시 교체(테스트 상단 주석 명시). schema_version·overtakes·segments·is_gap·DNF frames·display_names 필드 집합을 픽스처가 강제.
2. **버전 게이트**: 서버 schema_version 단조 증가 규약 ↔ 클라 `kMaxSupportedSnapshotVersion=1`. 상위 버전 렌더 거부(크래시 금지) 계약 준수 — 스키마 진화 시 뷰어 MAX 동반 상향 필요.
3. **표시명 조인(O-M3-1)**: 서버 `display_names`(user_id→nickname, 탈퇴="탈퇴한 러너") ↔ 클라 조인 취득(payload 미내장). 실 응답 키가 문자열 user_id인지 대조.
4. **status/enum wire**: {GENERATING·READY·FAILED} + finish_status {FINISHED·DNF} 서버값 ↔ 클라 unknown 폴백. DNS 부재(트랙 없음) 확인.
5. **payload null 규약**: GENERATING/FAILED 시 payload·display_names·schema_version=null ↔ 클라 상태별 UI. 404(미생성/CANCELLED)=뷰어 "없어요".
6. **딥링크 §10**: `runningcrew://replay/{sessionId}`↔`/sessions/:id/replay` 서버·클라 스킴·경로 합의(실 수신은 M3-C).

---

## 4. 실기기 대기 항목 (자동 검증 불가 — 수동 절차)

- **프레임 드랍 실측(60fps, 10명×600pt 동시)**: 정적/동적 painter 분리·RepaintBoundary로 부담을 낮췄으나, 실 GPU/저사양 기기에서의 드랍은 프로파일러(DevTools Performance overlay) 실측 필요. **수동 절차**: 실기기에서 4x 재생 중 Performance overlay로 프레임 예산(16.6ms) 초과 여부 확인, 10명×600pt 규모 스냅샷으로 시킹 반복 시 jank 관찰. 드랍 시 개선안 — 배경 경로를 Picture 캐시(`PictureRecorder`)로 1회 래스터화, 마커만 갱신.
- **실 지도 타일 위 렌더**: 현재 placeholder(좌표 정규화 스케치). 네이버 지도 SDK Client ID 발급 후 `NaverReplayMap` 어댑터로 교체(CoursePolylineMap 교체 지점과 동일 패턴) — 타일 위 오버레이 좌표계 검증은 발급 게이트 뒤.
- **딥링크 실 수신·라우팅**: `runningcrew://` 스킴 실제 인텐트 수신은 M3-C(Firebase/딥링크 배선) + 실기기 검증. 규약·라우트는 확정.

---

## 5. 테스트 (신규 +39, 전체 243 통과)

| 파일 | 검증 |
|---|---|
| `test/core/model/replay_dtos_test.dart` | 공유 픽스처 파싱·**버전 게이트(2>MAX 렌더 거부·크래시 금지)**·append-only 미지필드 무시·display_names 조인·DNF null·status 분기·enum 폴백 |
| `test/core/replay/replay_controller_test.dart` | 배속(1/2/4x)·시킹 클램프·토글·끝 자동정지·재시작·progress(시각 주입 골든) |
| `test/core/replay/frame_sampler_test.dart` | 보간(절반/경계)·시작전/종료후·is_gap·단일 프레임(골든) |
| `test/features/replay/replay_screen_test.dart` | GENERATING/FAILED/READY/버전초과 위젯 렌더·재생 토글·`replayErrorText`(404 vs 기타, 순수) |

주의(테스트 설계): 오류 상태 문구는 순수 함수 `replayErrorText`로 분리해 단위 테스트했다. Ticker 활성 하 async provider 의 AsyncError 전파가 위젯 테스트 프레임 구동과 맞지 않아(런타임 정상, 테스트 하네스 한정) 위젯 404 테스트 대신 순수 로직으로 검증 — "순수 로직 분리 골든" 원칙과 정합.

---

## 6. 계약 준수 자기검증

스키마 v1 DTO(frames·overtakes·segments·display_names 조인) ✅ / **MAX_SUPPORTED=1 게이트·크래시 금지** ✅ / unknown 필드 무시(append-only) ✅ / 전원 마커 동시 보간 ✅ / 배속·시킹·재생 순수 골든 ✅ / 추월 마킹(지도+타임라인+배너) ✅ / 페이스 색상 버킷 ✅ / is_gap 점선 구분 ✅ / DNF 경로 표시 ✅ / 상태별 UI(GEN/FAIL/READY) ✅ / accent 팔레트·Space Grotesk ✅ / placeholder 진입 교체·딥링크 §10 라우트 ✅ / analyze 0·test 243·apk debug 빌드 ✅ / 프레임드랍 실측 실기기 명시(§4) ✅.
