# track-api 계약

> **v0.1 · 2026-07-04 · 신규(contract-first)** — 서버 구현(M2-A A2/A3/A8)·앱 소비(M2-B)는 이 문서 기준(3자 대조의 진실).
> 근거: `42_analyst_design_M2A.md`, `domain-model` 스킬 Tracking·Ranking 컨텍스트, 계획서 §4 레이스 규칙·§5.3·§5.4, `V1__init.sql`(track_record·track_payload·race_result·rank_entry). 공통 규약은 `conventions.md`(v0.1.2 — 오류코드·§9 배열 시각 예외).
>
> **변경 이력**
> - v0.1 (2026-07-04, domain-analyst): 신규. 트랙 업로드(인코딩 폴리라인+병렬배열, 멱등/재업로드 정책 O-M2-4)·업로드 상태 조회·결과/순위 조회. client_meta 3키 격리. epoch millis 시각 배열.

모든 엔드포인트 `auth: required` (`Authorization: Bearer {access_token}` — 401 규약은 auth-api.md §3).

---

## 0. 트랙 payload 표현 규약 (상호운용 진실)

트랙은 **인코딩 폴리라인 1개 + 동일 길이 병렬 배열 N개**로 전송한다. 원시 포인트 배열을 JSON 객체 리스트로 보내지 않는다(전송 크기·파싱 비용).

| 요소 | 표현 | 비고 |
|---|---|---|
| 좌표열 | **Google Encoded Polyline, precision 1e5**(course-api와 동일 규약) | 클라 `PolylineCodec` 재사용. tie=half-away-from-zero. 디코딩 결과 = N 포인트 |
| `timestamps` | **epoch milliseconds (int64) 배열**, 길이 N | **GPS 시각 우선**(기기 시계 아님, 계획서 §5.3). conventions §9 예외 적용. UTC 기준 절대시각 |
| `speeds` | double(m/s) 배열, 길이 N | 각 포인트 순간 속도 |
| `accuracies` | double(m) 배열, 길이 N | 수평 정확도. 정제 시 임계 필터 입력 |
| `altitudes` | double(m) 배열, 길이 N — **선택(nullable 배열 전체)** | 있으면 길이 N 강제, 없으면 생략. 고도는 판정에 미사용 |

**배열 길이 일치 불변식(TK-1)**: `decode(polyline).length == timestamps.length == speeds.length == accuracies.length == (altitudes?.length)`. 하나라도 불일치 → `400 TRACK_ARRAY_LENGTH_MISMATCH`.

**시간 정합성(TK-2)**: `timestamps`는 **비내림차순**(단조 증가, 동시각 허용). 역순·미래(수신 시각 대비 과도한 미래) 발견 시 `400 TRACK_PAYLOAD_INVALID`. 서버는 폴리라인 디코딩 실패(<2점 포함) 시에도 동일 코드.

**크기 상한(TK-3)**: 포인트 수 ≤ **20,000**, 요청 본문 ≤ **8 MiB**. 초과 시 `413 TRACK_TOO_LARGE`. (근거: 3초 샘플 × 6시간 ≈ 7,200점 — 여유 3배. 상한은 서버 설정 외부화.)

---

## 오류 코드 (본 API 전수)

`VALIDATION_ERROR`(400) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `SESSION_STATE_INVALID`(409) · `TRACK_ALREADY_UPLOADED`(409) · `TRACK_PAYLOAD_INVALID`(400) · `TRACK_ARRAY_LENGTH_MISMATCH`(400) · `TRACK_TOO_LARGE`(413) · `RESULT_NOT_READY`(409)

---

## 1. POST /api/v1/sessions/{sessionId}/track — 트랙 업로드

**참가자 본인**. 완주 후(로컬 FINISHED_LOCAL) 사후 업로드. 선 register 필요(participation 부재 시 409). 세션 상태 `OPEN|RUNNING|FINALIZING`에서 수신(마감 직전 도착 내성 — FINALIZING 중 아직 미확정이면 수용, 결과 확정 후엔 거부). `COMPLETED|CANCELLED|DRAFT`는 409.

> **CANCELLED 세션 예외(계획서 §5.2)**: 취소된 세션의 트랙은 이 엔드포인트로 받지 않되, 클라가 보유한 트랙은 "개인 기록(세션 무관 주행)"으로 보존한다 — 개인기록 저장 경로는 M2-C 코스승격②와 함께 별도 정의(현 계약 범위 밖, 여기 명시만).

### 요청
```json
{
  "client_upload_id": "b3f1c2a4-...-uuid",
  "started_at": "2026-07-10T21:00:05Z",
  "polyline": "_p~iF~ps|U_ulLnnqC...",
  "timestamps": [1752181205000, 1752181208000, 1752181211000],
  "speeds": [0.0, 2.8, 3.1],
  "accuracies": [12.0, 8.5, 9.0],
  "altitudes": [11.0, 11.2, 11.1],
  "client_meta": { "os": "android", "os_version": "14", "device_model": "SM-S911N" }
}
```
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| client_upload_id | string(uuid) | 필수 | **멱등 키**. 한 번의 완주당 클라가 1회 생성·재시도 간 재사용(§4 재업로드 정책) |
| started_at | datetime | 필수 | 시작 버튼 시각(GPS 시각 우선). `track_record.started_at`. UTC ISO-8601(단일 시점이므로 §3 규약) |
| polyline | string | 필수, ≥2점 | 1e5 인코딩 좌표열 |
| timestamps | int64[] | 필수, 길이 N, 비내림차순 | epoch millis, GPS 시각(§0) |
| speeds | double[] | 필수, 길이 N | m/s |
| accuracies | double[] | 필수, 길이 N | m |
| altitudes | double[]? | 선택, 있으면 길이 N | m |
| client_meta | object? | 선택 | **`{os, os_version, device_model}` 3키만**(conventions §8). 서버 저장·디버깅 전용, 판정 미사용 |

- 서버는 **원시(raw)를 `track_payload.raw_payload`에 저장**하고(무손실 보존), **TrackRefinementService로 정제 → `refined_payload`**, 정제 후 좌표로 `total_distance_m` 계산, FinishPolicy로 완주/DNF·`finished_at`·`total_time_s` 확정한다(전부 동기).
- `finished_at`은 요청에 없음 — **서버가 정제 트랙에서 도착점 반경 최초 진입 시각으로 자동 확정**(계획서 §4). 클라 '종료' 버튼 시각은 신뢰하지 않음.

### 응답 201 (신규 수용) / 200 (동일 멱등 키 재요청)
```json
{
  "track_record_id": 4021,
  "session_id": 91,
  "user_id": 7,
  "processing_status": "PROCESSED",
  "finish_status": "FINISHED",
  "started_at": "2026-07-10T21:00:05Z",
  "finished_at": "2026-07-10T21:26:41Z",
  "total_distance_m": 5043,
  "total_time_s": 1596,
  "gps_gap_count": 1
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| track_record_id | int64 | 생성된 레코드 id |
| session_id / user_id | int64 | |
| processing_status | string(enum) | {`PROCESSED`} — M2는 동기 처리라 항상 PROCESSED. (비동기 도입 시 `RECEIVED`/`PROCESSING` 확장 예약) |
| finish_status | string(enum) | {`FINISHED`, `DNF`} — FinishPolicy 결과. DNS는 미출주라 트랙 없음(업로드 경로 밖) |
| started_at | datetime | UTC |
| finished_at | datetime? | 완주 시 도착 최초 진입 시각. **DNF면 null** |
| total_distance_m | int | 정제 후 좌표 기반(원시 하버사인 누적 아님) |
| total_time_s | int? | 그로스 타임(finished−started). **DNF면 null**(레이스 기록 미성립) |
| gps_gap_count | int | 식별된 GPS 유실 구간 수(리플레이 보간 메타, M3 소비) |

### 오류
- `400 TRACK_PAYLOAD_INVALID` — 폴리라인 디코딩 실패(<2점)·timestamps 역순/미래.
- `400 TRACK_ARRAY_LENGTH_MISMATCH` — 병렬 배열 길이 불일치(TK-1).
- `400 VALIDATION_ERROR` — 필수 필드 누락·client_meta 미허용 키.
- `403 FORBIDDEN` — 참가자 아님.
- `404 NOT_FOUND` — 세션 없음.
- `409 SESSION_STATE_INVALID` — participation 부재(선 register 필요) 또는 세션이 OPEN/RUNNING/FINALIZING 아님(COMPLETED/CANCELLED/DRAFT).
- `409 TRACK_ALREADY_UPLOADED` — 다른 `client_upload_id`로 이미 업로드된 participation(§4 불변).
- `413 TRACK_TOO_LARGE` — 크기 상한 초과(TK-3).

---

## 2. GET /api/v1/sessions/{sessionId}/track/me — 내 업로드 상태 조회

**참가자 본인**. 업로드·처리 결과 확인(재시도 판단·결과 대기 화면). 트랙 미업로드면 404.

### 응답 200
§1 응답과 동일 shape(`track_record_id`…`gps_gap_count`). payload 블롭은 미포함(track_record 요약만 — 블롭 격리 원칙).

### 오류
- `403 FORBIDDEN` — 참가자 아님. · `404 NOT_FOUND` — 세션 없음/내 트랙 미업로드.

---

## 3. GET /api/v1/sessions/{sessionId}/result — 결과·순위 조회

세션 결과 확정(`race_result` 존재, 세션 COMPLETED) 후 순위표. **미확정 세션은 `409 RESULT_NOT_READY`**(클라는 결과 대기 화면 유지). 크루 멤버 조회 가능.

### 응답 200
```json
{
  "session_id": 91,
  "course": { "id": 55, "name": "한강 5K", "distance_m": 5000 },
  "finalized_at": "2026-07-11T09:00:03Z",
  "entries": [
    { "user_id": 3, "nickname": "민수",       "status": "FINISHED", "rank": 1, "record_time_s": 1502, "total_distance_m": 5021, "avg_pace_s_per_km": 299, "is_pb": true },
    { "user_id": 7, "nickname": "지현",       "status": "FINISHED", "rank": 1, "record_time_s": 1502, "total_distance_m": 5008, "avg_pace_s_per_km": 300, "is_pb": false },
    { "user_id": 5, "nickname": "현우",       "status": "FINISHED", "rank": 3, "record_time_s": 1640, "total_distance_m": 5033, "avg_pace_s_per_km": 326, "is_pb": true },
    { "user_id": 8, "nickname": "다은",       "status": "DNF",      "rank": null, "record_time_s": null, "total_distance_m": 3120, "avg_pace_s_per_km": null, "is_pb": false },
    { "user_id": 9, "nickname": "탈퇴한 러너", "status": "DNS",      "rank": null, "record_time_s": null, "total_distance_m": null, "avg_pace_s_per_km": null, "is_pb": false }
  ]
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| session_id | int64 | |
| course | object | `id int64`, `name string`, `distance_m int` |
| finalized_at | datetime | `race_result.finalized_at` UTC |
| entries | array | 완주자 rank 오름차순 → DNF → DNS 순 정렬 |
| entries[].user_id | int64 | 탈퇴해도 행 보존 |
| entries[].nickname | string | 탈퇴 시 `"탈퇴한 러너"` 익명(rank_entry는 익명 보존) |
| entries[].status | string(enum) | {`FINISHED`, `DNF`, `DNS`} — Participation 최종값 부분집합 |
| entries[].rank | int? | 완주자만. **동률 공동순위+다음 건너뜀(1,1,3)**. DNF/DNS는 null |
| entries[].record_time_s | int? | 완주 그로스 타임. DNF/DNS null |
| entries[].total_distance_m | int? | 정제 후 거리. DNS(트랙 없음) null, DNF는 뛴 만큼 |
| entries[].avg_pace_s_per_km | int? | record_time_s / (distance/1000). 완주자만 |
| entries[].is_pb | bool | **완주 기록만 PB 후보**(O-M2-5). DNF/DNS 항상 false |

### 오류
- `403 FORBIDDEN` — 크루 비멤버. · `404 NOT_FOUND` — 세션 없음. · `409 RESULT_NOT_READY` — 결과 미확정(FINALIZING 이전 또는 확정 전).

---

## 범위 밖 (여기 명시만 — M2-C/M3)

- **구간 페이스(TrackSegment 500m)**·**추월 지점**: 리플레이 스냅샷 소관(M3). 정제 시 서버가 계산해 `refined_payload`/스냅샷에 내장하나 결과 API v0.1엔 미노출.
- **개인 기록 히스토리(유저×코스 PB 목록)**·**CANCELLED 세션 개인기록 보존**: M2-C(코스승격②와 함께).
- **리플레이 스냅샷 조회**·FCM 리플레이 알림: M3(별도 replay-api).
