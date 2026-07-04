# course-api 계약

> **v0.1 · 2026-07-04 · 신규(contract-first)** — 서버 구현(B2-S1)·앱 소비(B2-C2/C3)는 이 문서 기준(3자 대조의 진실).
> 근거: `22_analyst_design_B2.md` §1(Course 애그리거트·폴리라인 1e5·발행 후 불변), `domain-model` 스킬 Race 컨텍스트, `V1__init.sql` course DDL. 공통 규약은 `conventions.md`.
>
> **변경 이력**
> - v0.1 (2026-07-04, domain-analyst): 신규. 코스 생성(크루장)·목록·상세. 폴리라인 precision 1e5·tie half-away-from-zero 규약. 발행 후 불변(수정/삭제 API 미노출) 의미론.

모든 엔드포인트 `auth: required` (`Authorization: Bearer {access_token}` — 401 규약은 auth-api.md §3).

## 폴리라인 인코딩 규약 (상호운용 진실 — 서버·클라 문자 단위 일치)

- 형식: **Google Encoded Polyline Algorithm** (zigzag + 5비트 청크).
- **precision: 1e5 (좌표 ×100000)** — **1e6 아님**. 클라 `PolylineCodec._precision=100000`과 일치해야 함. 불일치 시 좌표 10배 어긋남.
- **tie 반올림: half-away-from-zero** (정확히 .5는 절댓값 큰 쪽). Dart `num.round()`와 동형. 서버는 `Math.round()`(half-up — 음위도/음경도에서 상이) **금지**.
- `route_polyline`은 코스 경로의 인코딩 문자열. 디코딩·미리보기·완주 판정(M2)에서 재사용.

## 발행 후 불변 의미론 (계획서 §5.2 — "수정 필요 시 새 코스 생성")

- Course는 **불변 애그리거트**: 본 API는 **생성·조회만** 제공. **수정/삭제 엔드포인트 없음** → 발행 후 불변을 구조로 보장.
- `COURSE_IMMUTABLE`(409)는 **예약 코드**: 향후 코스 삭제/관리 API 도입 시 OPEN 이상 세션이 참조하는 코스에 대한 변경/삭제를 거부하는 의미론. v0.1에는 트리거 경로 없음(수정/삭제 API 부재 + FK RESTRICT로 방어).

## 오류 코드 (본 API 전수)

`VALIDATION_ERROR`(400) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `CREW_CLOSED`(409) · `COURSE_IMMUTABLE`(409, 예약)

---

## 1. POST /api/v1/crews/{crewId}/courses — 코스 생성

**크루장 전용**. 인코딩 폴리라인 + 메타 수신. `distance_m`은 **서버가 폴리라인에서 계산·확정**(클라 제출값 불신).

### 요청
```json
{
  "name": "한강 5K",
  "route_polyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
  "start_lat": 37.5121, "start_lng": 127.0018,
  "finish_lat": 37.5288, "finish_lng": 127.0219
}
```
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| name | string | 필수, trim 후 1~50자 | V1 `course.name VARCHAR(50)` |
| route_polyline | string | 필수, 비어있지 않음 | 1e5 인코딩. 서버가 디코딩 검증(≥2점) |
| start_lat / start_lng | double | 필수 | 출발점(리플레이 t=0 기준·표시) |
| finish_lat / finish_lng | double | 필수 | 도착점(반경 30m 완주 판정 기준 — M2) |

- **미규정 — 제안**: `distance_m`은 요청에서 **받지 않음**(서버가 폴리라인 하버사인 누적으로 확정). 클라가 참고용으로 계산해 보내더라도 서버는 무시·재계산.

### 응답 201
코스 상세(§3 CourseDetail). `distance_m`은 서버 계산값.

### 오류
- `400 VALIDATION_ERROR` — name 길이/누락, polyline 빈 값·디코딩 실패(<2점), 좌표 범위 오류.
- `403 FORBIDDEN` — 크루장 아님.
- `404 NOT_FOUND` — crew 없음.
- `409 CREW_CLOSED` — CLOSED 크루.

---

## 2. GET /api/v1/crews/{crewId}/courses — 코스 목록

크루의 코스 목록. 페이지네이션 래퍼(`conventions.md` §6). `created_at` 내림차순 기본. **세션 생성 UI의 코스 선택 소스**(B2-C2).

### 응답 200
```json
{
  "items": [
    {
      "id": 55,
      "crew_id": 12,
      "name": "한강 5K",
      "distance_m": 5000,
      "created_at": "2026-07-04T09:00:00Z"
    }
  ],
  "page": 0, "size": 20, "total_elements": 1, "total_pages": 1
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | course id |
| crew_id | int64 | |
| name | string | |
| distance_m | int | 서버 확정(폴리라인 기반) |
| created_at | datetime | UTC |

- 목록은 폴리라인 미포함(경량). 미리보기·상세는 §3.

---

## 3. GET /api/v1/courses/{courseId} — 코스 상세

### 응답 200 (CourseDetail)
```json
{
  "id": 55,
  "crew_id": 12,
  "name": "한강 5K",
  "route_polyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
  "distance_m": 5000,
  "start_lat": 37.5121, "start_lng": 127.0018,
  "finish_lat": 37.5288, "finish_lng": 127.0219,
  "created_by": 3,
  "created_at": "2026-07-04T09:00:00Z"
}
```
| 필드 | 타입 | 비고 |
|---|---|---|
| id | int64 | |
| crew_id | int64 | |
| name | string | |
| route_polyline | string | 1e5 인코딩. 클라 `PolylineCodec.decode`로 미리보기 |
| distance_m | int | 서버 확정 |
| start_lat / start_lng | double | 출발점 |
| finish_lat / finish_lng | double | 도착점 |
| created_by | int64 | 생성자 user_id |
| created_at | datetime | UTC |

### 오류
- `403 FORBIDDEN` — 비멤버 조회(코스는 크루 소유).
- `404 NOT_FOUND` — 없는 courseId.

---

## 범위 밖 (여기 명시만 — 이후 배치)

- 코스 **수정·삭제 API**: 발행 후 불변 원칙상 v0.1 미노출(불변 애그리거트). 삭제/관리 도구 도입 시 `COURSE_IMMUTABLE` 게이트(OPEN 이상 세션 참조 거부) 동반.
- 지도에서 경로 그리기(탭 좌표 수집) 등록 UI: 네이버 지도 Client ID 대기(계획 §4). v0.1은 인코딩 폴리라인 직수신 + dev 시드 코스로 성립.
- distance_m 정제 후 주행거리 비교(완주 판정): M2 FinishPolicy 소관.
