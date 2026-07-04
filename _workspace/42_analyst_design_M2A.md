# 42 — 도메인 설계·계약 확정안 (M2-A: 서버 심장부 — Tracking·Ranking·마감 전이)

> 작성: domain-analyst · 2026-07-04 · 기준: `41_planner_plan_M2.md`(M2-A 11작업), `22_analyst_design_B2.md`(Race 컨텍스트 — 유효), `domain-model` 스킬(Tracking/Ranking/레이스 규칙), 계획서 §4·§5.3·§5.4, `V1__init.sql`(track_record/track_payload/race_result/rank_entry DDL 확정), `docs/contracts/`(conventions v0.1.2·course v0.1·session v0.2·track v0.1 신규)
> B2 설계와 충돌 없음 — 본 문서는 Tracking·Ranking·마감 전이 델타. 상태머신·계약 shape는 append.

---

## 0. 오케스트레이터 확정 반영 + planner 판단 3건 확정

### 반영(오케스트레이터 확정)
- **O-M2-1**: '레이스 시작' 활성 버튼 정본(session-api §6 start). M2-B에서 실 트래커 연결. — 본 설계는 서버측만, 영향 없음.
- **O-M2-3**: FinishPolicy/TrackSegment **임계값 초기값 = 계획서 명시값**(도착 30m / 거리 90% / 일치율 80% / 코리도 50m / 세그먼트 500m). **설정 외부화만** 요구(하드코딩 금지). accuracy 정제 임계 등 계획서 미규정 값은 §3.2에 "미규정—제안"으로 별도.

### 확정(planner 위임 판단 3건 — 계획서 근거)

| # | 질문 | 확정 | 계획서 근거 |
|---|---|---|---|
| **O-M2-2** | FINALIZING에서 취소 가능? | **불가 — `409 SESSION_STATE_INVALID`** | 계획서 §5.2 취소 목적 = "크루장은 **RUNNING 중에도** 취소(우천·사고 등 **현실 대응**)". FINALIZING은 물리 레이스가 끝나고 전원업로드/deadline이 성립한 뒤의 **순수 산정 단계** → 중단할 레이스가 없다. M2에서 FINALIZING→COMPLETED는 동기 확정(짧은 순간)이라 취소 창구도 실질 부재. B2 매트릭스가 이미 "FINALIZING cancel=409(M2)"로 예약한 것과 정합. 뛴 트랙은 어차피 결과에 보존되므로 노력 증발 없음. |
| **O-M2-4** | 마감 전 재업로드 정책 | **최초 1회 채택·이후 불변. 멱등 키(`client_upload_id`) 동일 재요청=기존결과 200, 다른 내용 재업로드=`409 TRACK_ALREADY_UPLOADED`. "더 나은 기록 갱신" 불허** | 계획서 §5.3 "**트랙은 업로드 시점에 한 번 쓰이고 불변**". 갱신을 열면 이 규범 직접 위반. 또 §4 그로스타임+도착 최초진입 자동확정이라 "더 나은 기록" 개념 자체가 없음(재측정 여지 없음). 단 클라 `upload_queue`가 지수백오프 재시도(성공응답 유실 케이스 존재)하므로 **동일 키 멱등 재수용은 필수**(R로컬우선 원칙). |
| **O-M2-5** | DNF 기록 PB 후보? | **아니오 — 완주(FINISHED) 기록만 PB 후보. DNF/DNS는 `is_pb=false` 고정** | 계획서 §5.4 "PB는 유저×코스 기준", PB=최고 **기록**. DNF는 도착 미진입 → `record_time_s`/`total_time_s` NULL(레이스 기록 미성립) → 비교 대상 부재. planner 제안과 일치. |

---

## 1. 업로드 페이로드 설계 (A1 계약 — track-api.md v0.1 신규)

### 1.1 표현: 인코딩 폴리라인(1e5) + 병렬 배열
- 좌표열 = **Google Encoded Polyline precision 1e5**(course-api와 **동일** 규약 — 클라 `PolylineCodec` 재사용, tie=half-away-from-zero). 원시 포인트를 JSON 객체 배열로 보내지 **않는다**(크기·파싱).
- 병렬 배열 4종, **전부 길이 N**(=decode(polyline) 포인트 수): `timestamps`(epoch millis int64, **GPS 시각 우선**), `speeds`(m/s), `accuracies`(m), `altitudes`(선택 — 있으면 길이 N).
- `started_at`(단일 시점, ISO-8601 UTC): 시작 버튼 시각. `track_record.started_at`의 진실.
- `client_meta`: **`{os, os_version, device_model}` 3키 고정**(conventions §8). 서버 저장·디버깅 전용, **판정 절대 미사용**(플랫폼 무지).
- 크기 상한: 포인트 ≤ 20,000, 본문 ≤ 8 MiB(외부화). 초과 `413 TRACK_TOO_LARGE`.

### 1.2 페이로드 불변식
| # | 불변식 | 강제 | 위반 시 |
|---|---|---|---|
| TK-1 | 배열 길이 일치: `N == timestamps == speeds == accuracies == (altitudes?)` | 수신 검증(A3) | `400 TRACK_ARRAY_LENGTH_MISMATCH` |
| TK-2 | timestamps 비내림차순(단조↑, 미래 아님) — **시간 정합성만**(A3 범위) | 수신 검증(A3) | `400 TRACK_PAYLOAD_INVALID` |
| TK-3 | 폴리라인 디코딩 ≥2점 | A3 | `400 TRACK_PAYLOAD_INVALID` |
| TK-4 | client_meta 미허용 키 거부(3키만) | A3 | `400 VALIDATION_ERROR` |
| TK-5 | 시각은 GPS 시각(기기 시계 아님) — 클라 계약 준수 | 계약·M2-B | (클라 책임, 서버 강제 불가 — 계약 명시) |

> **A3 범위 경계(계획서 §5.3·planner A3)**: TrackUploadService는 **디코딩 + 시간 정합성 검증만**. **코스 이탈 검증을 여기서 하지 않는다** — 완주/이탈 판정은 **FinishPolicy로 일원화**(중복 구현 0). A3는 "받아서 저장·정제 트리거"까지.

---

## 2. 저장 경계 — TrackRecord / TrackPayload (A2)

### 2.1 애그리거트 & 1:1 분리
- **TrackRecord**(애그리거트 루트, `tracking/domain`): `id, sessionId, userId, startedAt, finishedAt?, totalDistanceM?, totalTimeS?`. 순위·PB·히스토리·상태 조회의 **유일한 진입점**.
- **TrackPayload**(1:1, PK=FK): `rawPayload`(무손실 원시), `refinedPayload?`(정제 결과 — 재정제로 갱신). **@OneToOne 연관 두지 않음**(V1 주석 — 조회에 블롭 딸림 방지).
- **원시+정제 이중 보존**(계획서 §5.3): raw는 업로드 시 즉시 저장(재정제 가능), refined는 정제 산출.

### 2.2 payload 접근 격리 (이월 흡수 — track_payload 격리)
- **일반 조회 포트**(`LoadTrackRecordPort` 등): track_record만 SELECT. **payload 조인 0건**.
- **payload 전용 포트**(`LoadTrackPayloadPort` — 별도 인터페이스): **리플레이 생성(M3)·재정제 전용**. 순위/결과/상태 조회 어댑터는 이 포트를 주입받지 않는다(구조적 격리).
- **QA 재검증 트리거**: 순위·결과 조회 경로(A8 result API)가 등장하므로 **A7/A8 완료 후 payload 조인 0건 재확인**(planner A2 흡수 명시). 검증: result/status 조회 SQL 로그에 `track_payload` 부재.

### 2.3 불변식
| # | 불변식 | 강제 |
|---|---|---|
| TR-1 | track_record : track_payload = 1:1(PK=FK CASCADE) | 스키마(V1) |
| TR-2 | UQ(session_id, user_id) — participation당 트랙 1개 | 스키마 UQ + A3 |
| TR-3 | 조회 경로 payload 조인 0건(블롭 격리) | 포트 분리 + QA 로그 검증 |
| TR-4 | raw 무손실 보존, refined는 재정제로만 갱신(raw 불변) | A3·A4 코드 |
| TR-5 | 탈퇴 시 raw/refined 삭제, track_record 행·rank_entry는 익명 보존 | 기존 `TrackDataEraser` 경로(B1) 재사용 — payload CASCADE는 record 삭제 시나, 탈퇴는 payload만 삭제·record 익명 보존 |

> TR-5 주의: 탈퇴는 **payload만 삭제**(record는 익명 보존). 기존 `TrackPayloadEraseAdapter`가 payload row 삭제. track_record는 남아 rank_entry/리플레이 정합 유지(계획서 §4 익명 보존).

---

## 3. TrackRefinementService — 순수 함수 명세 (A4)

**순수**(IO·시계·랜덤 금지). 입력: raw 포인트열(polyline+병렬배열 디코딩) + 정제 파라미터. 출력: 정제 포인트열 + 거리 + GPS공백 메타. **골든 대상**(실주행 픽스처 ≥1).

### 3.1 파이프라인(순서 고정)
1. **accuracy 임계 필터**: `accuracy > refine.accuracy_max_m` 포인트 제거.
2. **이상 점프 보정**: 연속 포인트 순간속도 `> refine.max_speed_mps` (Δdist/Δt)면 이상점으로 제거(또는 직전 유효점으로 클램프 — 제거 채택, 단순·결정적).
3. **이동평균 스무딩**: 창 크기 `refine.smoothing_window`(홀수)로 좌표 이동평균.
4. **정제 후 거리**: 정제 좌표열 **하버사인 누적** → `total_distance_m`. **원시 하버사인 누적 금지**(계획서 §5.3 — 노이즈 부풀림).
5. **GPS 공백 식별**: 인접 유효 포인트 간 `Δt > refine.gap_threshold_s`면 공백 구간 → `gaps[]` 메타(start_idx/end_idx/Δt). 리플레이 보간용(M3), 개수는 결과 응답 `gps_gap_count`.

- **그로스 타임 불변(planner 흡수)**: 정제는 **정지 구간을 삭제·보정하지 않는다** — 신호대기 포함 실경과 유지. 자동 일시정지 없음(계획서 §4). accuracy/점프 필터는 노이즈 제거지 정지 제거가 아님.

### 3.2 파라미터 & 설정 키
| 키 | 초기값 | 근거 |
|---|---|---|
| `refine.accuracy_max_m` | **50** (미규정—제안) | 계획서 미규정. 코스 코리도 50m와 정합하는 보수값 제안. **사용자/운영 승인 대상** |
| `refine.max_speed_mps` | **12** (미규정—제안) | ≈43km/h — 러닝 상한+GPS오차 여유. 명백한 순간이동만 제거 |
| `refine.smoothing_window` | **3** (미규정—제안) | 최소 스무딩(과평활 시 코너 뭉개짐 방지) |
| `refine.gap_threshold_s` | **30** (미규정—제안) | 적응형 샘플 최대 10s(정지)의 3배 — 진짜 유실만 공백 처리 |

> 전부 **설정 외부화**(O-M2-3 원칙 확장). 초기값은 계획서 미규정이라 **제안 후 진행** — 골든 픽스처 축적 시 튜닝. backend는 config 바인딩만, 로직은 파라미터 주입.

---

## 4. FinishPolicy — 순수 함수 명세 (A6)

**순수**. 입력: 정제 포인트열 + Course(폴리라인 디코딩·finish 좌표·distance_m) + 임계값. 출력: `FINISHED|DNF` + `finishedAt?`. **코스 이탈 검증 일원화 지점**(중복 구현 0). **골든 대상**.

### 4.1 3조건 (전부 AND → FINISHED, 하나라도 미충족 → DNF)
| 조건 | 판정 | 설정 키(초기값 O-M2-3) |
|---|---|---|
| ① 도착 반경 | 정제 포인트 중 finish 좌표로부터 `≤ finish.radius_m`인 것 존재 | `finish.radius_m` = **30** |
| ② 거리 | `total_distance_m(정제) ≥ course.distance_m × finish.min_distance_ratio` | `finish.min_distance_ratio` = **0.90** |
| ③ 코스 일치율 | 정제 포인트의 `≥ finish.coverage_ratio` 비율이 코스 폴리라인으로부터 `≤ finish.corridor_m` | `finish.coverage_ratio` = **0.80**, `finish.corridor_m` = **50** |

### 4.2 finished_at 자동 확정 (계획서 §4)
- **도착점 반경 최초 진입 시각** = ①을 만족하는 **최초** 정제 포인트의 timestamp(GPS 시각). `track_record.finished_at`.
- `total_time_s = finished_at − started_at`(그로스 타임). 종료 버튼 시각 **불신**(늦게 눌러도 손해 없음).
- **DNF**: ①~③ 중 미충족 → `finished_at = NULL`, `total_time_s = NULL`(레이스 기록 미성립). **단 정제 트랙·total_distance_m는 보존**(리플레이 표시·계획서 §4). ① 미진입(도착 안 함)이 전형적 DNF.
- 안티치트 아님(계획서 §4 명시) — 지름길/오진입의 **정직한 판정**.

### 4.3 골든 케이스(test-engineer 인계)
정상완주 / 지름길 주파(②거리 미달 DNF) / 다른길 오진입(③일치율 미달 DNF) / 도착 미진입(①미충족 DNF) / 경계값(정확히 90%·80%·30m·50m — 등호 포함 여부 고정) / GPS 공백 있는 완주.

---

## 5. TrackSegment (A5) · RankingPolicy (A7) · PB · 이벤트 흐름

### 5.1 TrackSegment — 구간 페이스(순수, A5)
- 정제 트랙을 누적거리 `segment.length_m`(=**500**, 외부화) 경계로 분할 → 각 구간 소요시간·평균페이스. 리플레이 색상용(M3 소비).
- **M2 노출 경계**: 순수함수+골든만 구축. 결과 API v0.1엔 **미노출**(refined_payload/스냅샷 내장은 M3). 등호 경계(정확히 500m 지점)·마지막 미완구간 처리 골든.

### 5.2 RankingPolicy — 순위 산정(순수, A7)
- 입력: 참가자별 `(userId, finishStatus, recordTimeS?, priorPbTimeS?)`. 출력: RankEntry 목록.
- **완주자 `record_time_s` 오름차순**. **동률 공동순위 + 다음 건너뜀 (1,1,3)**(계획서 §5.4).
- **DNF/DNS**: `rank = NULL`, 목록 **하단**. 정렬: 완주(rank↑) → DNF → DNS.
- **PB 판정**: 완주자만. `is_pb = (priorPbTimeS == null) || (recordTimeS < priorPbTimeS)`. 유저×course_id 기준(코스 불변이라 참조 안정). 첫 완주는 항상 PB. **DNF/DNS `is_pb=false` 고정**(O-M2-5).
  - priorPbTimeS 산출: 동일 user×course의 **과거 확정 결과(다른 세션)** 중 최소 완주기록. 같은 세션 내 비교 아님.
- 골든: 동률·DNF/DNS 혼합·PB 갱신/최초/미갱신·전원 DNF.

### 5.3 이벤트 흐름 (M2 = 동기 확정까지, AFTER_COMMIT 리플레이는 M3)
```
TrackUploaded(AFTER_COMMIT, A10) ─▶ Race: 전원 업로드 여부 재평가
        │                              └─ 충족 시 RUNNING→FINALIZING
마감 스케줄러(A9, deadline) ────────────▶ OPEN|RUNNING→FINALIZING
FINALIZING ─▶ SessionClosePolicy(A9): participation 최종화(DNF/DNS)
          ─▶ RaceCompleted ─▶ RankingPolicy(A7) 산정 ─▶ RaceResult+RankEntry 저장(A8)
          ─▶ ResultFinalized ─▶ FINALIZING→COMPLETED   ★여기까지 M2 동기★
ResultFinalized ─(커밋 후 비동기)─▶ ReplaySnapshot 생성   ← M3(이번 범위 밖)
```
- **A10 통지**: `TrackUploaded`는 **AFTER_COMMIT** 리스너(업로드 커밋 후 재평가 — 컨텍스트 경계 이벤트로만). 직접 Race 리포지토리 호출 금지.
- FINALIZING→COMPLETED 산정은 **동기 트랜잭션**(M2 규모 충분). 리플레이 생성만 M3에서 AFTER_COMMIT 분리(계획서 §5.6 — 확정이 계산에 인질 안 잡히게).

---

## 6. SessionClosePolicy — 순수 함수 + 마감 스케줄러 (A9)

### 6.1 순수 함수(clock 주입 테스트)
- 입력: `sessionStatus, uploadDeadline, now(주입), participations[](status, hasUploaded, finishStatus?)`.
- **RaceCompleted 트리거**: (STARTED 참가자 **전원 업로드**) **OR** (`now ≥ uploadDeadline`).
- **참가자 최종화**(마감 시점):
  | 현 participation | 조건 | 최종 |
  |---|---|---|
  | STARTED + 업로드 완주 | FinishPolicy=FINISHED | **FINISHED** |
  | STARTED + 업로드 미완주 | FinishPolicy=DNF | **DNF** |
  | STARTED + **미업로드** | deadline 도달까지 트랙 없음 | **DNF**(계획서 §8 "미업로드자 DNF") |
  | REGISTERED(미출주) | start 신호 없음 | **DNS**(신청 후 미출주) |
- **미규정-2 흡수**: STARTED 신호 유실로 세션이 OPEN에 남은 경우도 **OPEN→FINALIZING 직행 허용**(신호 유실 내성). 스케줄러는 **OPEN·RUNNING 모두** deadline 도달 시 FINALIZING 진입.

### 6.2 스케줄러(어댑터 — 순수함수 감쌈)
- 주기 폴링: `status ∈ {OPEN, RUNNING} AND now ≥ upload_deadline` 세션 → FINALIZING 전이 후 확정 파이프라인.
- **idempotent**: 이미 FINALIZING/COMPLETED면 no-op(중복 실행·재기동 내성). clock 주입으로 deadline 전이 재현 테스트(A9 수용).
- `upload_deadline`은 도메인 NOT NULL(V1). "예정+12h"는 앱레이어 기본값(하드코딩 금지 — B2 유지).

---

## 7. 불변식 체크리스트 (qa용 — M2-A 전수)

| # | 불변식 | 강제 수단 | 대응 작업 |
|---|---|---|---|
| TK-1 | 병렬 배열 길이 일치(N) | 수신 검증 → 400 | A3 |
| TK-2 | timestamps 단조↑·미래 아님(시간 정합성) | A3 → 400 | A3 |
| TK-3~5 | 폴리라인 ≥2점 / client_meta 3키 / GPS시각 | A3 + 계약 | A3 |
| TR-1~4 | 1:1 · UQ(session,user) · **조회 payload 조인 0** · raw 불변 | 스키마 + 포트 분리 + QA 로그 | A2, **A7/A8 후 재검증** |
| TR-5 | 탈퇴 payload 삭제·record/rank_entry 익명 보존 | 기존 Eraser 경로 재사용 | A2 |
| FR-1 | 거리 = **정제 후** 좌표(원시 하버사인 금지) | A4 순수함수 + 골든 | A4 |
| FR-2 | 정제는 정지구간 삭제 안 함(그로스타임) | A4 골든 | A4 |
| FR-3 | GPS 공백 메타 식별 | A4 + gps_gap_count | A4 |
| FP-1 | 완주=3조건 AND, 미충족=DNF(기록 보존) | A6 순수 + 골든 | A6 |
| FP-2 | finished_at=도착 반경 최초 진입 시각(종료버튼 불신) | A6 골든 | A6 |
| FP-3 | 임계값 30/90%/80%/50m 전부 외부화 | config 바인딩 | A5·A6 |
| FP-4 | 코스이탈 검증 **FinishPolicy 일원화**(A3 중복 0) | 코드 리뷰 | A3·A6 |
| RK-1 | 완주 오름차순·**동률 공동순위 건너뜀(1,1,3)** | A7 골든 | A7 |
| RK-2 | DNF/DNS rank NULL·하단 | A7 골든 | A7 |
| RK-3 | PB=완주만·유저×course_id·과거세션 최소기록 비교 | A7 골든 | A7 |
| RK-4 | **DNF/DNS is_pb=false**(O-M2-5) | A7 골든 | A7 |
| CL-1 | RaceCompleted=전원업로드 OR deadline | A9 clock 주입 | A9 |
| CL-2 | STARTED미업로드→DNF, REGISTERED→DNS | A9 골든 | A9 |
| CL-3 | OPEN·RUNNING 모두 FINALIZING 허용(미규정-2) | A9 | A9 |
| CL-4 | 스케줄러 idempotent | A9 | A9 |
| SS-1 | FINALIZING 취소 불가(O-M2-2) 409 | 도메인 가드 | A9 |
| UP-1 | 재업로드: 동일 키 200 멱등 / 다른내용 409(O-M2-4) | A3 멱등 | A3 |
| EV-1 | TrackUploaded AFTER_COMMIT·직접 Race 호출 0 | 이벤트 리스너 + ArchUnit | A10 |
| EV-2 | 컨텍스트 경계 직접 조인/참조 0 | 코드 리뷰 | A2·A10 |
| RE-1 | rank_entry `rank` 컬럼 백틱 인용(R-003 이월5) | JPA 매핑 | A8 |
| RE-2 | rank_entry user FK RESTRICT(탈퇴 익명 보존) | 스키마 | A8 |

---

## 8. 계약 산출 (A1)

- **`docs/contracts/track-api.md` 신규 v0.1**: 업로드(§1, 멱등·재업로드 O-M2-4)·업로드 상태 조회(§2)·결과/순위 조회(§3). payload 표현 규약(§0 — 폴리라인 1e5+병렬배열, epoch millis, client_meta 3키). 오류 전수.
- **`docs/contracts/conventions.md` v0.1.1 → v0.1.2**: §4 code 5종 추가(TRACK_*·RESULT_NOT_READY), §8 client_meta 3키 고정 명문화, §9 대량 배열 시각 예외(epoch millis) 신설.
- **`docs/contracts/session-api.md`**: **무변경**(v0.2 유지). 결과 조회는 track-api §3으로 배치(M2 트랙/결과 응집). session-api §범위밖이 이미 "결과·순위 조회 M2"로 예약 — 포인터만, v0.3 승격 불요. FINALIZING/COMPLETED 전이는 B2 매트릭스가 이미 명시(M2 실구현).

---

## 9. backend-dev / test-engineer 주의사항

**backend-dev (2)**:
- **payload 조회 격리**: 순위/결과/상태 조회 어댑터에 `LoadTrackPayloadPort` 주입 금지 — track_record 포트만. A7/A8 완료 후 조회 SQL 로그에 `track_payload` 조인 0건 QA 재검증(TR-3). `rank_entry.rank`는 **백틱 인용**(R-003 이월5 — red→green 회귀 연결).
- **코스 이탈 검증 A3에 넣지 말 것** — FinishPolicy 일원화(FP-4). A3는 디코딩+시간정합성+저장+정제 트리거까지. TrackUploaded는 **AFTER_COMMIT** 리스너로만 Race 통지(직접 리포지토리 호출 0, EV-1).

**test-engineer (2)**:
- 순수함수 4종(TrackRefinement·TrackSegment·FinishPolicy·RankingPolicy) **IO·시계·랜덤 0** 확인 후 골든. FinishPolicy **경계 등호**(정확히 30m/90%/80%/50m)·SessionClosePolicy는 **clock 주입**으로 deadline 전이 재현(A9).
- 임계값은 **파라미터 주입**(하드코딩 금지 — FP-3). 실주행 픽스처 ≥1 축적(A4 회귀선). 거리검증은 **정제 후 좌표 기준 기대값**(원시 하버사인 금지 — FR-1) 박제.

---

## 10. 미규정 잔여 (오케스트레이터 에스컬레이션 — 진행은 제안값으로)

- **미규정-A4 (정제 파라미터 초기값)**: `accuracy_max_m=50 / max_speed_mps=12 / smoothing_window=3 / gap_threshold_s=30` — 계획서 미규정(§3.2). **제안값으로 외부화·진행**, 골든 픽스처 축적 후 튜닝. 사용자/운영 승인 요망(O-M2-3의 계획서 명시값과 달리 이건 미규정).
- **미규정 (PB 과거 비교 범위)**: 유저×course_id 과거 완주기록 = **다른 세션의 확정 결과** 최소값. 같은 코스가 여러 세션에서 재사용될 때 성립(코스 불변 전제). CANCELLED 세션 개인기록은 PB 모수 제외(순위·보상 미생성 — 계획서 §5.2). 제안 확정, 이견 시 회신.
- **CANCELLED 개인기록 보존 경로**: 계획서 §5.2 "뛰던 트랙 개인기록 보존"의 저장 경로는 **M2-C**(코스승격②와 함께). track-api §1은 CANCELLED 업로드를 받지 않음(명시). 범위 밖 표기.
