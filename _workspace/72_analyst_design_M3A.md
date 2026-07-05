# 72 — 도메인 설계·계약 확정안 (M3-A: 리플레이 스냅샷 파이프라인)

> 작성: domain-analyst · 2026-07-05 · 기준: `71_planner_plan_M3.md`(A1~A10), 계획서 §5.6 Replay·§5 이벤트 흐름(규범 원문), `domain-model` 스킬 Replay, `42_analyst_design_M2A.md`(TrackSegment·payload 격리·이벤트 흐름), `V1__init.sql`(replay_snapshot DDL — 완비), `docs/contracts/`(conventions v0.1.3·track v0.1.2·history v0.1)
> 별도 애그리거트 없음 — 프로젝션 계층 `replay` 패키지. 본 문서는 스냅샷 payload 스키마 v1이 핵심 산출물.

---

## 0. 오케스트레이터 확정 반영 + 위임 판단 2건

### 반영
- **O-M3-1**: 스냅샷 payload는 **user_id만 내장**, 표시명(nickname)은 **조회 시 조인**(탈퇴 익명화 정합 — 스냅샷 재생성 없이 "탈퇴한 러너" 반영). → §1 스키마·§4 조회에 반영.
- **O-M3-4**: RewardGranted 알림 = **인앱 갈음**(FCM 2종 원칙 보존). M3-C RewardGrant 생성 소관, 본 문서 범위 밖.
- **O-M3-5**: 정제 파라미터 기확정(accuracy 50/speed 12/window 3/gap 30) 유지 — 리플레이는 refined 소비만, 재상정 없음.

### 확정(위임 — 계획서 근거)

| # | 질문 | 확정 | 근거 |
|---|---|---|---|
| **O-M3-2** | schema_version 초기값·버저닝 규약 | **정수 `schema_version = 1` 시작**(replay_snapshot.schema_version INT). **뷰어는 자신이 아는 `MAX_SUPPORTED_VERSION` 보유 — 스냅샷 version > MAX면 "앱 업데이트 필요" 안내(렌더 거부), ≤ MAX면 렌더.** 하위호환은 뷰어가 구버전 스키마도 읽을 수 있게 유지(추가 필드는 append-only). | 계획서 §5.6 "버저닝 덕에 표현 진화해도 과거 레이스 안 깨짐". 미지 상위 버전=클라 구버전 → 크래시 금지·업데이트 유도(app-version 게이트 철학과 정합). 정수 단조증가면 비교 단순 |
| **O-M3-3** | 딥링크 규약 | **스킴 `runningcrew://`. 경로: `runningcrew://session/{sessionId}`(세션 상세)·`runningcrew://replay/{sessionId}`(리플레이 뷰어).** go_router 경로 `/sessions/:id`·`/sessions/:id/replay`에 매핑. FCM payload의 `data.deep_link`에 full URI 탑재. | 계획서 §5.6 FCM "리플레이 열림" 트리거. 스냅샷은 session당 1개(재생성 시 최신)이므로 리플레이 딥링크 키 = sessionId(snapshotId 아님 — 재생성 내성). conventions에 규약화 |

---

## 1. ReplaySnapshot 스키마 v1 (payload JSON — 핵심 설계물)

`replay_snapshot.payload`(LONGTEXT)에 저장되는 JSON. **READY일 때만 존재**(GENERATING/FAILED는 NULL). 서버 사전계산 완결 — 클라는 계산 없이 렌더만.

### 1.1 최상위 구조
```json
{
  "schema_version": 1,
  "session_id": 91,
  "course": {
    "distance_m": 5000,
    "route_polyline": "_p~iF~ps|U…",
    "start": { "lat": 37.5121, "lng": 127.0018 },
    "finish": { "lat": 37.5288, "lng": 127.0219 }
  },
  "duration_ms": 1680000,
  "participants": [ /* §1.2 */ ],
  "overtakes": [ /* §1.3 */ ]
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| schema_version | int | =1. 뷰어 호환 판정 키(O-M3-2) |
| session_id | int64 | |
| course | object | 코스 폴리라인·거리·start/finish(뷰어 배경 경로) |
| duration_ms | int64 | **상대 시각 최댓값**(모든 참가자 트랙 중 t=0 대비 최대 경과) — 슬라이더 길이 |
| participants | array | §1.2. **user_id만**(표시명 조인) |
| overtakes | array | §1.3 추월 이벤트 사전계산 |

### 1.2 참가자별 상대시각 트랙 (A1 — t=0 병합)
```json
{
  "user_id": 7,
  "finish_status": "FINISHED",
  "finish_time_ms": 1596000,
  "frames": [
    { "t_ms": 0,     "lat": 37.5121, "lng": 127.0018, "cum_dist_m": 0,    "is_gap": false },
    { "t_ms": 3000,  "lat": 37.5124, "lng": 127.0021, "cum_dist_m": 41,   "is_gap": false },
    { "t_ms": 33000, "lat": 37.5140, "lng": 127.0050, "cum_dist_m": 380,  "is_gap": true  }
  ],
  "segments": [ /* §1.4 색상 구간 */ ]
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| user_id | int64 | **표시명 미내장**(O-M3-1 — 조회 시 조인). 탈퇴 익명화 정합 |
| finish_status | string(enum) | {`FINISHED`,`DNF`}. DNS는 트랙 없음 → 스냅샷 부재 |
| finish_time_ms | int64? | 완주 시각(상대). **DNF는 null**(조기 종료 — frames 마지막이 트랙 끝) |
| frames | array | **각자 시작 t=0 상대 시각** 정렬. refined 좌표(원시 아님). `t_ms`(상대 경과), `lat/lng`, `cum_dist_m`(누적 진행거리 — 추월 계산 기준), `is_gap`(GPS 유실 보간 구간 — 뷰어 점선/구분 표시) |
| segments | array | §1.4 |

- **t=0 정렬(A1)**: 각 참가자 `t_ms = frame.gps_time − started_at`(GPS 시각 기준). 시작 시각 상이해도 t=0 정렬로 "동시 출발 고스트" 병합(계획서 §5.6 "2차 비동기 레이스도 로직 변경 없이 지원").
- **DNF 포함**: DNF 참가자도 frames 보존(뛴 만큼) — 계획서 §4 "DNF도 리플레이 표시". `finish_time_ms=null`, frames는 트랙 끝까지.
- **is_gap**: TrackRefinement가 식별한 GPS 공백 구간(gap_threshold_s 초과). 뷰어가 보간 표시(실측 vs 보간 구분).

### 1.3 추월 이벤트 (A2 — 동일 진행거리 도달 시각 비교, 사전계산)
```json
{ "at_dist_m": 2500, "passer_user_id": 7, "passed_user_id": 3, "t_ms": 720000 }
```
| 필드 | 타입 | 비고 |
|---|---|---|
| at_dist_m | int | 추월 발생 진행거리(추월 판정 기준거리) |
| passer_user_id | int64 | 추월한 사람 |
| passed_user_id | int64 | 추월당한 사람 |
| t_ms | int64 | 추월 발생 상대 시각(뷰어 마킹 위치) |

- **수학적 정의(§3.2)**: 두 참가자 A·B의 "진행거리 d 도달 상대시각" `T_A(d)`, `T_B(d)`를 비교. `T_A(d) < T_B(d)`(A가 d를 먼저 통과)의 부호가 d 증가에 따라 뒤집히는 지점이 추월. 경계는 §3.2.

### 1.4 구간 페이스 색상 (A3 — TrackSegment 500m 재사용)
```json
{ "seg_index": 0, "start_dist_m": 0, "end_dist_m": 500, "pace_s_per_km": 288, "color_bucket": 2 }
```
| 필드 | 타입 | 비고 |
|---|---|---|
| seg_index | int | 0-base |
| start_dist_m / end_dist_m | int | 구간 경계(500m, M2 TrackSegmentService 재사용) |
| pace_s_per_km | int | 구간 평균 페이스 |
| color_bucket | int | 페이스→색상 버킷(§3.3 순수 매핑). 뷰어 색상표 인덱스 |

### 1.5 크기 추정 & 상한
- 10명 × 600 frame × (t_ms int + lat/lng double + cum_dist int + is_gap bool) ≈ frame당 ~50 bytes(JSON) → 600×50 = 30KB/인, 10명 = **~300KB** + overtakes(수십)·segments(인당 10구간) ≈ **총 350~450KB**.
- **상한: `replay.snapshot.max_bytes = 2 MiB`(외부화)** — 초과 시 생성 FAILED(비정상 대용량 방어). LONGTEXT는 4GB까지 수용하나 뷰어 로드·전송 관점 실질 상한. 정상 규모(계획서 10명×600pt)의 ~4배 여유.

---

## 2. schema_version 규약 (O-M3-2 확정)

- **초기값 1**(정수 단조 증가).
- **뷰어 호환 판정**: 클라 `MAX_SUPPORTED_SNAPSHOT_VERSION` 상수 보유.
  - `payload.schema_version ≤ MAX` → 렌더(구버전 스키마도 읽음 — 하위호환 유지, 신규 필드는 append-only·optional).
  - `payload.schema_version > MAX` → **"앱 업데이트 필요" 안내**(렌더 거부, 크래시 금지 — R-001 정신). app-version 강제업데이트와 별개의 뷰어 레벨 게이트.
- **스키마 진화 규칙**: 필드 추가는 optional append-only(구뷰어 무시 가능)면 version 유지, 의미 변경/제거는 version 증가. 계약(replay-api)에 version별 필드 델타 기록.
- **재생성 멱등**: 원시 트랙 보존 하 삭제→최신 스키마 재생성 결과 동일(§4.3). 재생성 시 schema_version은 그 시점 서버 상수값(구 스냅샷보다 높을 수 있음 — 아카이브 최신화).

---

## 3. 순수 함수 3종 명세 (골든 기대값 도출 수준)

전부 **IO·시계·랜덤 없는 순수 함수**(`replay` 패키지). 입력=refined 트랙(참가자별)+started_at. **골든 대상**.

### 3.1 병합(A1) — `mergeToRelativeTimeline(tracks) → participants[]`
- 각 참가자 refined 좌표열을 `t_ms = gps_time − started_at`로 변환·정렬(비내림차순).
- `cum_dist_m` = refined 좌표 하버사인 누적(프레임별). `is_gap` = TrackRefinement gap 메타 반영.
- `duration_ms` = 전 참가자 최대 `finish_time_ms`(또는 DNF 트랙 최대 t_ms).
- **골든 경계**: 시작시각 상이 참가자 정렬 / DNF 조기종료(짧은 frames) / GPS 공백 프레임 is_gap 표기 / 단일 참가자.

### 3.2 추월(A2) — `computeOvertakes(participants) → overtakes[]`
- **진행거리→시각 함수** `T_u(d)`: 참가자 u가 누적거리 d에 도달한 상대시각(frames의 cum_dist_m 선형보간).
- **추월 정의**: 참가자 쌍 (A,B)에 대해 부호함수 `sign(T_A(d) − T_B(d))`가 d 증가에 따라 **음→양으로 뒤집히는** 최소 d = A가 B를 추월한 지점. `t_ms` = 그 d에서의 `max(T_A(d), T_B(d))`(둘 다 통과한 시점 = 추월 완료).
- **경계 케이스(골든 필수)**:
  | 케이스 | 처리 |
  |---|---|
  | **동시 도달**(`T_A(d)=T_B(d)`) | 추월 이벤트 아님(부호 0 — 순간 병렬). 명확히 교차(부호 반전)해야 추월 |
  | **재역전**(A→B 추월 후 B→A 재추월) | 각 반전마다 별도 overtake 이벤트(순서대로 2건) |
  | **DNF 조기 종료** | DNF 참가자의 `T(d)`는 도달 최대거리까지만 정의. 그 너머 d는 비교 대상 제외(추월 미발생) |
  | 두 트랙 진행거리 범위 미교집합 | 공통 도달 거리 없음 → overtake 없음 |
- 결정성: 동일 입력 → 동일 overtakes 순서(d 오름차순, 동일 d는 user_id 오름차순 tie-break).

### 3.3 색상(A3) — `paceColorBuckets(segments) → segment[].color_bucket`
- TrackSegment(500m 페이스, M2 `TrackSegmentService` 재사용) → `pace_s_per_km` → **버킷 매핑**(순수·결정적). 예: 페이스 구간 경계 배열 `color.pace_buckets_s_per_km`(외부화)로 인덱스 산출.
- **골든**: 경계 페이스값(버킷 경계 정확히)·마지막 미완 구간(500m 미만 잔여)·전 구간 동일 페이스.

---

## 4. 생성 파이프라인·상태 전이 (A4·A5·A6)

### 4.1 상태 전이 (replay_snapshot.status)
```
(ResultFinalized 커밋 후 비동기 A5) ─▶ 행 생성 GENERATING (payload=NULL)
GENERATING ─(병합·추월·색상 계산 성공)─▶ READY (payload 저장) ─▶ [최초 READY] FCM 트리거(M3-C)
          └(예외)─▶ FAILED (payload=NULL, 관측·재시도 대상)
```
- **GENERATING**: 계산 시작 시 즉시 행 생성(관측 가능 — "생성 중" 뷰어 UI 근거).
- **READY**: payload 저장 완료. 조회 시 payload 반환.
- **FAILED**: 계산 예외 시. **조용한 실패 금지**(계획서 §5.6) — 상태로 남겨 재시도·관측.

### 4.2 AFTER_COMMIT 비동기 경계 (A5)
- `ResultFinalized` **AFTER_COMMIT** 리스너 + `@Async`(잡 큐 불요 — MVP 10명×600pt 수백ms). 확정 트랜잭션과 분리(확정이 계산에 인질 안 잡힘 — 계획서 §5.6).
- **컨텍스트 경계**: replay는 track_payload(refined)를 **전용 native 포트로만** 접근(직접 Tracking 리포지토리·서비스 호출 금지 — 이벤트/포트로만). §6 불변식.

### 4.3 재생성 운영 도구 — **관리 API로 확정**(vs 스크립트)
- **확정: 관리 API**(`POST /api/v1/admin/sessions/{id}/replay/regenerate` — admin 인증). 근거: 계획서 §5.6 "관리 API **또는** 스크립트" 중, (1) 운영 중 원격 실행 가능(SSH 스크립트보다 접근성), (2) 인증·감사 로그가 API 게이트에 자연 통합, (3) FAILED 관측(A6)과 같은 admin 표면에 응집. 스크립트는 API를 감싸는 편의로 후순위.
- **멱등**: replay_snapshot은 복수 행 허용(V1 인덱스). 재생성 = 새 행 INSERT(GENERATING→READY), 최신 = `created_at` max. **원시 트랙 보존 하 결과 동일성**(순수함수라 입력 같으면 payload 동일, schema_version만 최신).
- **FCM 재발송 금지**: 재생성 READY는 `replay_notified_at` 이미 set이면 재발송 안 함(§5).

### 4.4 FCM 세션당 1회 멱등 (M3-C 경계 — 본 문서는 포트 호출까지)
- 최초 READY 도달 시 `NotificationSender` 포트 호출 + `race_session.replay_notified_at` set(원자적). 이미 set이면 no-op(재생성·중복 이벤트 내성). 실 FCM 어댑터는 Firebase 게이트 뒤(M3-C §6), 규약·멱등은 지금 확정.

---

## 5. 조회 이벤트 로깅 (A9 — M4 성공기준 측정)

- **목적**: 계획서 §8 "레이스 후 24h 내 리플레이 조회율 80%" 판정 수단. 없으면 성공기준 측정 불가.
- **기록 시점**: `GET /replay/{sessionId}` READY 응답 반환 시. 구조화 로그 + 집계 저장.
- **필드**: `session_id`, `user_id`(조회자), `viewed_at`(UTC), `finalized_at`(세션 결과 확정 — 24h 창 기준점). 파생 지표: `viewed_within_24h = (viewed_at − finalized_at ≤ 24h)`.
- **집계 형태**: session별 (참가자 수, 24h내 고유 조회자 수) → 조회율. M4 확인 루틴 데이터소스.
- **개인정보**: user_id만(닉네임·위치 없음). 탈퇴 시 로그는 익명 파생물(식별자 분리 — 계획서 §5 원칙).

---

## 6. 불변식 체크리스트 (qa용 — payload 접근 정당 경로 명시)

| # | 불변식 | 강제 |
|---|---|---|
| **RP-1** | **스냅샷 생성이 track_payload(refined)를 읽는 것은 정당 경로** — replay는 payload 전용 native 포트의 **명시적 소비자**(리플레이 생성·재정제와 동급). 조회 API(§4 GET result·history)의 "payload 조인 0" 불변식과 **구분** | 포트 주입 경계 — replay만 payload 포트 주입, 순위/결과/히스토리 어댑터는 여전히 미주입 |
| RP-2 | replay는 Tracking/Ranking 리포지토리·서비스 **직접 호출 0**(이벤트/전용 포트로만) | ArchUnit R-2 + 코드 리뷰 |
| RP-3 | payload에 **표시명 미내장**(user_id만) — 조회 시 조인, 탈퇴 익명화 정합 | §1.2 스키마 + A8 조인 |
| RP-4 | 병합 t=0 상대시각·refined 좌표(원시 아님) | A1 골든 |
| RP-5 | 추월 사전계산·순수·결정적(동시도달·재역전·DNF 경계) | A2 골든 |
| RP-6 | DNF 참가자 frames 보존(finish_time_ms null) | A1·A3 골든 |
| RP-7 | GENERATING/FAILED payload NULL, READY만 payload | A4 코드 |
| RP-8 | FAILED 관측·재시도 가능(조용한 실패 금지) | A6 |
| RP-9 | 생성 AFTER_COMMIT 비동기(확정 트랜잭션 분리) | A5 |
| RP-10 | 재생성 멱등(복수 행·원시보존 하 동일 결과·최신=created_at max) | A6 |
| RP-11 | schema_version 미지 상위 → 뷰어 "업데이트 필요"(크래시 금지) | 클라 + A7 계약 |
| RP-12 | FCM 세션당 1회(replay_notified_at)·재생성 재발송 금지 | M3-C 포트 + 멱등 |
| RP-13 | 스냅샷 크기 상한(2MiB) 초과 시 FAILED | A4 설정 |
| RP-14 | 조회 로깅 존재(M4 측정) — user_id만, 익명 파생 | A9 |
| RP-15 | 딥링크 키=sessionId(snapshotId 아님 — 재생성 내성) | conventions 딥링크 규약 |

---

## 7. 계약 산출

- **`docs/contracts/replay-api.md` 신규 v0.1**: 스냅샷 조회(§4 GET), status별 응답(READY=payload+표시명 조인 / GENERATING·FAILED=상태만), schema_version·미지버전 대응 규약, payload 스키마 v1 필드 전수, 조회 로깅 관점, 재생성 admin API, 비멤버 403.
- **`docs/contracts/conventions.md` v0.1.3 → v0.1.4**: §10 딥링크 규약 신설(`runningcrew://` 스킴·session/replay 경로·FCM data.deep_link).

---

## 8. backend / flutter / test 주의사항

**backend-dev (2)**:
- replay 생성만 track_payload **전용 native 포트**로 refined 접근(RP-1 — 정당 경로, 순위/결과/히스토리 어댑터엔 여전히 미주입). Tracking/Ranking 직접 호출 0(RP-2 — 이벤트/포트만). 생성은 ResultFinalized **AFTER_COMMIT + @Async**(RP-9), 예외 시 FAILED 저장(RP-8).
- 재생성=관리 API(admin 인증)·복수 행 INSERT·최신 created_at max(RP-10). FCM은 최초 READY만 포트 호출+replay_notified_at 원자적 set(RP-12).

**flutter-dev (2)**:
- 뷰어는 `MAX_SUPPORTED_SNAPSHOT_VERSION` 보유 — payload version 초과 시 "앱 업데이트 필요" 렌더 거부(RP-11, 크래시 금지). 표시명은 payload 아닌 조회 응답의 조인 필드에서 취득(RP-3).
- 딥링크 `runningcrew://replay/{sessionId}` → go_router `/sessions/:id/replay`. GENERATING/FAILED/READY 상태별 UI(생성중·재시도·뷰어). DNF 참가자 경로·is_gap 보간 구분 렌더.

**test-engineer (2)**:
- 순수 3종(병합·추월·색상) IO·시계·랜덤 0 확인 후 골든. **추월 경계**(동시도달=이벤트 아님·재역전=순서대로 N건·DNF 조기종료=범위 밖 미발생)·병합 t=0 정렬·색상 버킷 경계 기대값 박제.
- refined 좌표 기반 cum_dist 기대값(원시 하버사인 금지 — RP-4). 스냅샷 응답 공유 픽스처(A10 — schema_version·overtakes·segments·is_gap·DNF frames 필드 강제, P26-2 리플레이 확장).

---

## 9. 미규정 잔여 (에스컬레이션 — 진행은 제안값)

- **추월 최소 간격 필터**: GPS 노이즈로 미세 재역전이 다수 overtake를 만들 수 있음. **제안: v1은 순수 부호반전 전량 기록**(뷰어가 표시 밀도 조절), 노이즈 필터(최소 시간·거리 간격)는 골든 픽스처 관찰 후 도입. 이견 시 회신.
- **admin 인증 방식**: 재생성 관리 API의 admin 식별(별도 role vs 운영 토큰). **제안: M3-A는 규약만(admin 경로), 실 인증은 backend-dev 재량 + 운영 환경 게이트**(prod 노출 제한). 계획서 미규정.
- **조회 로깅 저장소**: 구조화 로그(집계 외부) vs 전용 테이블. **제안: M3는 구조화 로그 우선**(M4 확인루틴이 로그 집계), 대량화 시 테이블 승격. 계획서 §운영 "구조화 로그" 정합.
