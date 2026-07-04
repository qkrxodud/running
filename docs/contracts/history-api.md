# history-api 계약

> **v0.1 · 2026-07-05 · 신규(contract-first)** — 서버 구현(M2-C C4)·앱 소비(C5)는 이 문서 기준(3자 대조의 진실).
> 근거: `62_analyst_design_M2C.md` §2, `42_analyst_design_M2A.md` §5.2(PB 정의), `domain-model` 스킬 Ranking·Tracking, 계획서 §5.4, `V1__init.sql`(track_record·rank_entry·race_result). 공통 규약은 `conventions.md`(v0.1.3).
>
> **변경 이력**
> - v0.1 (2026-07-05, domain-analyst): 신규. 개인 기록 히스토리(§1)·코스별 PB(§2). 본인 한정. track_record 스캔 기반(payload 격리 유지). CANCELLED 세션 배지·DNF 포함(O-M2C-2).

모든 엔드포인트 `auth: required`. **본인 한정** — 토큰 sub의 user 기록만 반환(타인 조회 없음, MVP).

## Enum 값 집합 (R-001 — 클라 미지값 크래시 금지·unknown 폴백)

- `finish_status` (기록 히스토리 항목의 완주 판정): {`FINISHED`, `DNF`}
  - **DNS는 부재**: 미출주는 track_record가 없어 기록 히스토리에 나오지 않는다("뛴 기록만"). DNS는 세션 결과(track-api §3)에서만 하단 표기.

## 조회 원천 규약 (payload 격리 — HS-2)

- 히스토리·PB의 데이터 원천은 **track_record 스캔 + rank_entry 조인**(확정 세션). **track_payload(raw/refined 블롭) 조인 0건** — 블롭 격리 유지(계획서 §5.3, TR-3). QA payload 격리 3차 재검증 대상.
- PB 값은 **RankingPolicy PB 정의와 동일**(유저×`course_id` 과거 완주 최소 `record_time_s`). 새 정의 도입 없음 — 재집계일 뿐.

---

## 1. GET /api/v1/me/records — 내 기록 히스토리

본인이 실제 뛴 기록 목록(완주·DNF 전체 — O-M2C-2). 최신순(세션 `scheduled_at` 내림차순). 페이지네이션 래퍼(`conventions.md` §6).

### 응답 200
```json
{
  "items": [
    {
      "track_record_id": 4021,
      "session_id": 91,
      "course_id": 55,
      "course_name": "한강 5K",
      "scheduled_at": "2026-07-10T21:00:00Z",
      "finish_status": "FINISHED",
      "rank": 1,
      "record_time_s": 1502,
      "total_distance_m": 5021,
      "avg_pace_s_per_km": 299,
      "is_pb": true,
      "session_cancelled": false
    },
    {
      "track_record_id": 4102,
      "session_id": 88,
      "course_id": 55,
      "course_name": "한강 5K",
      "scheduled_at": "2026-07-03T21:00:00Z",
      "finish_status": "DNF",
      "rank": null,
      "record_time_s": null,
      "total_distance_m": 3120,
      "avg_pace_s_per_km": null,
      "is_pb": false,
      "session_cancelled": false
    },
    {
      "track_record_id": 4150,
      "session_id": 84,
      "course_id": 60,
      "course_name": "올림픽공원 3K",
      "scheduled_at": "2026-06-28T08:00:00Z",
      "finish_status": "FINISHED",
      "rank": null,
      "record_time_s": 1010,
      "total_distance_m": 3040,
      "avg_pace_s_per_km": 332,
      "is_pb": false,
      "session_cancelled": true
    }
  ],
  "page": 0, "size": 20, "total_elements": 3, "total_pages": 1
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| track_record_id | int64 | 승격 소스 참조(course-api §4) |
| session_id | int64 | |
| course_id | int64 | PB·승격 판정 기준 코스 |
| course_name | string | 편의(코스 불변이라 안정) |
| scheduled_at | datetime | 세션 예정 일시 UTC(정렬 키) |
| finish_status | string(enum) | {`FINISHED`,`DNF`} |
| rank | int? | 확정 세션 완주자만. DNF·**CANCELLED 세션 null**(순위 미산정) |
| record_time_s | int? | 완주 그로스 타임. DNF null |
| total_distance_m | int? | 정제 후 거리. DNF도 뛴 만큼(비null 일반) |
| avg_pace_s_per_km | int? | 완주자만 |
| is_pb | bool | **완주만 PB 후보**. DNF·CANCELLED 세션 항상 false |
| session_cancelled | bool | true면 "취소된 세션" 배지(개인 기록 보존 — 계획서 §5.2, C7). 이 항목은 rank/is_pb 없음 |

- CANCELLED 세션 트랙: 보존·노출(session_cancelled=true), 순위·PB 미산정(CX-2). 승격은 FINISHED면 가능(course-api §4).

### 오류
- (본인 조회라 403/404 거의 없음. 토큰 유효하면 빈 목록 가능)
- `401` — 인증 규약(auth-api §3).

---

## 2. GET /api/v1/me/personal-bests — 내 코스별 개인 최고기록

본인이 완주한 코스별 최고 기록(유저×course_id 최소 `record_time_s`). 완주 기록만(PB=완주만 — O-M2C-2). 페이지네이션 래퍼(코스 수 작으나 규약 통일).

### 응답 200
```json
{
  "items": [
    {
      "course_id": 55,
      "course_name": "한강 5K",
      "distance_m": 5000,
      "best_record_time_s": 1502,
      "avg_pace_s_per_km": 299,
      "achieved_session_id": 91,
      "achieved_at": "2026-07-10T21:00:00Z"
    }
  ],
  "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| course_id | int64 | |
| course_name | string | |
| distance_m | int | 코스 총거리(서버 확정값) |
| best_record_time_s | int | 최소 완주 기록(그로스 타임) |
| avg_pace_s_per_km | int | best 기록의 평균 페이스 |
| achieved_session_id | int64 | PB 달성 세션 |
| achieved_at | datetime | 달성 세션 `scheduled_at` UTC |

- 원천: 확정 세션 `rank_entry`의 FINISHED만 코스별 min. **CANCELLED 트랙·DNF 제외**(rank_entry 부재). RankingPolicy PB 정의와 동일 값(HS-3).

### 오류
- `401` — 인증 규약.

---

## 범위 밖 (여기 명시만 — 이후)

- **타인/크루 전체 히스토리·리더보드**: MVP 본인 한정. 2차.
- **거리대 PB**(코스 무관 5K 최고 등): 계획서 §5.4 "거리대 PB는 2차". 본 계약은 코스별 PB만.
- **기록 상세→리플레이 진입**: 리플레이 뷰어는 M3(replay-api).
