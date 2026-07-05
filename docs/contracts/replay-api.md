# replay-api 계약

> **v0.1 · 2026-07-05 · 신규(contract-first)** — 서버 구현(M3-A A8)·앱 뷰어(M3-B)는 이 문서 기준(3자 대조의 진실).
> 근거: `72_analyst_design_M3A.md`(스냅샷 스키마 v1·순수함수·상태전이), 계획서 §5.6 Replay, `domain-model` 스킬 Replay, `V1__init.sql`(replay_snapshot). 공통 규약은 `conventions.md`(v0.1.4 — 딥링크).
>
> **변경 이력**
> - v0.1 (2026-07-05, domain-analyst): 신규. 스냅샷 조회(§1) status별 응답·payload 스키마 v1(§2)·표시명 조인 규약·미지버전 대응·조회 로깅·재생성 admin(§3). user_id만 내장(O-M3-1), schema_version=1(O-M3-2).

모든 조회 엔드포인트 `auth: required`. **크루 멤버 조회 가능**(비멤버 403).

## Enum 값 집합 (R-001 — 클라 미지값 크래시 금지·unknown 폴백)

- `replay_snapshot.status` (**ReplayStatus**): {`GENERATING`, `READY`, `FAILED`}
  - `GENERATING`: 생성 중(뷰어 "준비 중" UI). payload 없음.
  - `READY`: 조회 시 payload 반환.
  - `FAILED`: 생성 실패(뷰어 "재시도/문의" UI). payload 없음. 운영 재생성(§3) 대상.
- `finish_status` (payload 참가자): {`FINISHED`, `DNF`} (DNS는 트랙 없음 → 스냅샷 부재).

## schema_version 규약 (O-M3-2)

- **정수, 초기값 `1`**. 뷰어는 `MAX_SUPPORTED_SNAPSHOT_VERSION` 상수 보유.
- `payload.schema_version ≤ MAX` → 렌더(구버전 스키마 하위호환 유지 — 신규 필드는 optional append-only).
- **`payload.schema_version > MAX` → 뷰어 "앱 업데이트 필요" 안내·렌더 거부**(크래시 금지). 미지 상위 버전 = 클라 구버전 신호.

## 표시명 조인 규약 (O-M3-1)

- payload에는 **user_id만 내장**(표시명 미저장). 조회 응답이 `display_names` 맵(user_id → nickname)을 **조회 시점 조인**으로 함께 반환 → 탈퇴 러너는 `"탈퇴한 러너"`로 반영(스냅샷 재생성 없이 익명화 정합).

## 오류 코드 (본 API 전수)

`FORBIDDEN`(403) · `NOT_FOUND`(404)

---

## 1. GET /api/v1/sessions/{sessionId}/replay — 리플레이 스냅샷 조회

세션의 최신 스냅샷(재생성 시 `created_at` max). status별 응답 분기. **크루 멤버만**. 조회 시 **조회 이벤트 로깅**(§조회 로깅).

### 응답 200 — status = READY
```json
{
  "status": "READY",
  "schema_version": 1,
  "display_names": { "3": "민수", "7": "지현", "8": "탈퇴한 러너" },
  "payload": {
    "schema_version": 1,
    "session_id": 91,
    "course": {
      "distance_m": 5000,
      "route_polyline": "_p~iF~ps|U…",
      "start": { "lat": 37.5121, "lng": 127.0018 },
      "finish": { "lat": 37.5288, "lng": 127.0219 }
    },
    "duration_ms": 1680000,
    "participants": [
      {
        "user_id": 7,
        "finish_status": "FINISHED",
        "finish_time_ms": 1596000,
        "frames": [
          { "t_ms": 0, "lat": 37.5121, "lng": 127.0018, "cum_dist_m": 0, "is_gap": false },
          { "t_ms": 3000, "lat": 37.5124, "lng": 127.0021, "cum_dist_m": 41, "is_gap": false }
        ],
        "segments": [
          { "seg_index": 0, "start_dist_m": 0, "end_dist_m": 500, "pace_s_per_km": 288, "color_bucket": 2 }
        ]
      },
      {
        "user_id": 8, "finish_status": "DNF", "finish_time_ms": null,
        "frames": [ { "t_ms": 0, "lat": 37.5121, "lng": 127.0018, "cum_dist_m": 0, "is_gap": false } ],
        "segments": []
      }
    ],
    "overtakes": [
      { "at_dist_m": 2500, "passer_user_id": 7, "passed_user_id": 3, "t_ms": 720000 }
    ]
  }
}
```

### 응답 200 — status = GENERATING / FAILED (payload 없음)
```json
{ "status": "GENERATING", "schema_version": null, "display_names": null, "payload": null }
```
```json
{ "status": "FAILED", "schema_version": null, "display_names": null, "payload": null }
```

| 최상위 필드 | 타입 | 비고 |
|---|---|---|
| status | string(enum) | ReplayStatus. GENERATING/FAILED면 payload·display_names null |
| schema_version | int? | READY만. payload.schema_version와 동일(최상위 편의 노출 — 뷰어 게이트 조기 판정) |
| display_names | object? | READY만. `{ "<user_id>": "<nickname>" }`. 탈퇴=`"탈퇴한 러너"`(조회 시점 조인) |
| payload | object? | READY만. §2 스키마 v1 |

### payload 필드 (§2 요약 — 전수는 설계 §1)
| 경로 | 타입 | 비고 |
|---|---|---|
| payload.schema_version | int | =1 |
| payload.session_id | int64 | |
| payload.course | object | `distance_m int`, `route_polyline string`(1e5), `start/finish {lat,lng double}` |
| payload.duration_ms | int64 | 슬라이더 길이(전 참가자 최대 상대시각) |
| payload.participants[] | array | `user_id int64`(표시명 미포함), `finish_status enum`, `finish_time_ms int64?`(DNF null), `frames[]`, `segments[]` |
| …frames[] | array | `t_ms int64`(상대), `lat/lng double`, `cum_dist_m int`(추월 기준), `is_gap bool`(GPS 유실 보간) |
| …segments[] | array | `seg_index int`, `start_dist_m/end_dist_m int`, `pace_s_per_km int`, `color_bucket int` |
| payload.overtakes[] | array | `at_dist_m int`, `passer_user_id/passed_user_id int64`, `t_ms int64`. 사전계산(동일 진행거리 도달 시각 비교 — 설계 §3.2) |

### 오류
- `403 FORBIDDEN` — 세션 소유 크루 비멤버.
- `404 NOT_FOUND` — 세션 없음 **또는 스냅샷 미생성**(CANCELLED 세션 등 리플레이 부재).

---

## 조회 로깅 (A9 — M4 성공기준 측정, 서버 내부)

- 본 API READY 응답 시 서버가 `(session_id, user_id, viewed_at, finalized_at)` 구조화 로그/집계 기록. 파생 지표 `viewed_within_24h`.
- **계약 표면 아님**(클라 무관) — 여기 명시는 3자 대조 시 "조회 로깅 존재" 확인용. user_id만(닉네임·위치 없음), 탈퇴 시 익명 파생.

---

## 2. 재생성 (운영 — admin)

- `POST /api/v1/admin/sessions/{sessionId}/replay/regenerate` (**admin 인증** — 일반 멤버 토큰 아님). 삭제→최신 스키마 재생성(복수 행 INSERT, 최신=created_at max). 원시 트랙 보존 하 멱등.
- FAILED 스냅샷 관측·재시도 경로. **FCM 재발송 안 함**(replay_notified_at 이미 set — 세션당 1회).
- admin 인증 방식은 운영 게이트(설계 §9 미규정) — 규약상 admin 전용 경로만 확정.

---

## 범위 밖 (여기 명시만)

- 뷰어 렌더(마커 보간·배속·슬라이더·추월 마킹·색상): M3-B 클라.
- FCM "리플레이 열림" 발송·딥링크 실동작: M3-C(Firebase 게이트). 딥링크 규약은 conventions §10.
- schema_version 2+ 필드 델타: 스키마 진화 시 본 문서에 version별 append 기록.
