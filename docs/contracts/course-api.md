# course-api 계약

> **v0.2 · 2026-07-05** — 서버 구현·앱 소비는 이 문서 기준(3자 대조의 진실).
> 근거: `22_analyst_design_B2.md` §1(Course 애그리거트·폴리라인 1e5·발행 후 불변), `62_analyst_design_M2C.md` §3(승격), `domain-model` 스킬 Race 컨텍스트, `V1__init.sql` course DDL. 공통 규약은 `conventions.md`(v0.1.3).
>
> **변경 이력**
> - v0.2 (2026-07-05, domain-analyst): M2-C — §4 코스 승격 엔드포인트 append(`POST /crews/{crewId}/courses/promote` — 내 과거 FINISHED 트랙 → 새 Course 발행). **자유생성(§1)은 크루장 유지, 승격(§4)은 크루 ACTIVE 멤버 누구나·본인 FINISHED 트랙만**(별도 유스케이스 — 실주행 기여). 거리 하한 1km·distance 서버 재확정·`COURSE_PROMOTION_INELIGIBLE`. §1~§3 무변경.
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

`VALIDATION_ERROR`(400) · `FORBIDDEN`(403) · `NOT_FOUND`(404) · `CREW_CLOSED`(409) · `COURSE_IMMUTABLE`(409, 예약) · `COURSE_PROMOTION_INELIGIBLE`(409, §4)

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

## 4. POST /api/v1/crews/{crewId}/courses/promote — 과거 주행 코스 승격 (M2-C)

**크루 ACTIVE 멤버 누구나**(크루장 전용 아님 — 자유생성 §1과 구분). 내가 실제 완주한 과거 트랙을 새 Course로 발행. 실주행 검증 경로를 크루 코스 풀에 기여하는 별도 유스케이스.

> **권한 구분(설계 §3.2)**: §1 자유생성(임의 폴리라인 직수신)은 크루장 전용 유지 — 세션 발행 재료 관리. §4 승격은 개인 실주행 기반이라 멤버 개방. 생성된 Course는 §1과 동일하게 **크루 소유·불변**, 세션 발행은 크루장 권한 그대로.

### 요청
```json
{
  "source_track_record_id": 4021,
  "name": "내가 뛴 한강 코스"
}
```
| 필드 | 타입 | 제약 | 비고 |
|---|---|---|---|
| source_track_record_id | int64 | 필수 | **본인** track_record만. history-api §1 `track_record_id` |
| name | string | 필수, trim 후 1~50자 | 새 코스명 |

### 승격 자격 게이트 (전부 충족해야 발행)
1. `source_track_record_id`가 **호출자 본인** 트랙(아니면 403).
2. **`finish_status = FINISHED`**(DNF·미완주 거부 — 경로 미완/이탈 가능).
3. **`total_distance_m ≥ promotion.min_distance_m`**(초기값 **1000m=1km**, 서버 설정 외부화). 미달 거부.
4. CANCELLED 세션의 본인 FINISHED 트랙도 승격 가능(트랙 품질은 완주·거리로 판정, 세션 취소는 코스 품질과 무관 — 설계 §8).

### 서버 처리 (불변 원칙 준수)
- **refined_payload**(정제 트랙)를 payload 전용 포트로 로드 → 폴리라인 인코딩(1e5) → 새 Course의 `route_polyline`.
- **`distance_m`은 refined 폴리라인에서 서버 재계산·확정**(CO-B3 — track_record 저장값·클라값 불신). `start/finish_lat/lng`은 refined 트랙 양 끝점.
- 새 Course는 **불변 애그리거트**(§1과 동일 — 수정/삭제 없음). `created_by` = 호출자.

### 응답 201
코스 상세(§3 CourseDetail). `distance_m`·좌표는 서버 확정값.

### 오류
- `400 VALIDATION_ERROR` — name 길이/누락, source_track_record_id 누락.
- `403 FORBIDDEN` — 크루 ACTIVE 멤버 아님 **또는 타인 트랙 승격 시도**(존재 누설 방지 — 본인 트랙 아니면 403).
- `404 NOT_FOUND` — crew 없음 / 트랙 없음(본인 트랙 부재).
- `409 CREW_CLOSED` — CLOSED 크루.
- `409 COURSE_PROMOTION_INELIGIBLE` — **미완주(DNF)** 또는 **거리 하한(1km) 미달**. (message로 사유 구분: 클라는 code로 분기, 상세 안내는 message.)

---

## 범위 밖 (여기 명시만 — 이후 배치)

- 코스 **수정·삭제 API**: 발행 후 불변 원칙상 미노출(불변 애그리거트). 삭제/관리 도구 도입 시 `COURSE_IMMUTABLE` 게이트(OPEN 이상 세션 참조 거부) 동반.
- 승격 **GPS 공백 상한** 게이트: v0.2는 거리 하한만. 공백 상한(gps_gap_count 기반)은 튜닝 데이터 축적 후(설계 §8).
- 지도에서 경로 그리기(탭 좌표 수집) 등록 UI: 네이버 지도 Client ID 대기(계획 §4). v0.1은 인코딩 폴리라인 직수신 + dev 시드 코스로 성립.
- distance_m 정제 후 주행거리 비교(완주 판정): M2 FinishPolicy 소관.
