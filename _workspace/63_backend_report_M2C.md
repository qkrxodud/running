# 63 — 백엔드 구현 보고 (M2-C: 결과 표면 — 히스토리·PB·코스 승격·CANCELLED 보존·kapi 503)

> 작성: backend-dev · 2026-07-05 · 기준: `61_planner_plan_M2C.md`(C4·C6·C7), `62_analyst_design_M2C.md`, 개정 계약(history-api v0.1·course-api v0.2 §4·track-api v0.1.2·auth-api v0.1.1·conventions v0.1.3)
> 선행: M2-A(43)·R-006/R-007/R-008 종결. 빌드: `./gradlew build` ✅ 189 tests. 라이브 곡선(업로드→취소→히스토리 배지→승격) ✅.

---

## 1. 빌드·라이브 결과

- **`./gradlew build` BUILD SUCCESSFUL — 189 tests**(M2-C 신규 4 테스트 클래스 포함). Testcontainers MySQL8 + Flyway V1~V3 + `ddl-auto=validate` 부팅 성공(신규 엔티티 0 — history/PB·승격은 전부 네이티브 SQL read/기존 course 재사용, 마이그레이션 불요).
- **라이브 곡선**(격리 mysql@3399 + bootRun local@8080, **sandbox 8081/3307·prod 3306 무접촉**): ① 업로드 FINISHED(dist 1548) → ② 세션 취소 CANCELLED → ③ **CANCELLED 세션 업로드 수락**(멤버, FINISHED — C7 핵심) → ④ CANCELLED 결과조회 **404 NOT_FOUND**(409 아님) → ⑤⑥ 확정 세션 업로드→자동 COMPLETED → ⑦ 히스토리 2건(취소: cancelled=true·rank null·pb false / 확정: rank 1·pb true) → ⑧ PB 1건(확정 세션만·취소 제외) → ⑨ **승격**(CANCELLED FINISHED 트랙, distance_m 1548 서버 재확정·created_by 리더) → ⑩ 타인 트랙 승격 **403**. 정리: 8080 해제·격리 mysql stop.

## 2. API·순수 함수 수

**신규 API 3개**(전부 auth required):
| 메서드 | 경로 | 컨텍스트 | 비고 |
|---|---|---|---|
| GET | `/api/v1/me/records` | ranking | 내 기록 히스토리(FINISHED+DNF, CANCELLED 배지), 최신순 페이징. 본인 한정 |
| GET | `/api/v1/me/personal-bests` | ranking | 코스별 PB(확정 세션 완주만), 페이징. 본인 한정 |
| POST | `/api/v1/crews/{crewId}/courses/promote` | race | 과거 FINISHED 트랙 → 새 불변 Course. 크루 멤버·본인·1km 하한 |

**변경 API 2개**: `POST /sessions/{id}/track`(CANCELLED 수락 추가 — C7), `GET /sessions/{id}/result`(CANCELLED → 404 — track-api v0.1.2 §3). `POST /auth/login`(kapi 장애 → 503 AUTH_KAKAO_UNAVAILABLE 분리).

**순수 함수 신규 0**(test-engineer 이관 대상 없음). 히스토리·PB는 SQL 집계(RankingPolicy PB 정의 재집계 — 새 정의 도입 없음, HS-3). 승격은 기존 `Course.create`(distance 서버 확정 CO-B3)·`PolylineCodec`·`TrackRefinementService` 재사용. avg_pace 계산은 어댑터 내 파생(순수 함수화 불요 — 골든 대상 아님).

## 3. qa 경계면 (payload 격리 3차 재검증 + M2-C 신규)

- **track_payload 격리 3차 재검증(HS-2·PR-6)**: 히스토리·PB 조회(`HistoryQueryAdapter`)는 track_record + race_session/course/race_result/rank_entry 조인만 — **`track_payload` 조인 0건**(SQL 내 미등장). 승격만이 `PromotionSourceAdapter`로 track_payload.refined_payload 접근(명시적 payload 소비자 PR-6 — 조회 어댑터엔 미주입, 구조적 격리). 라이브 히스토리/PB 응답에 블롭 미동반.
- **CANCELLED 의미론(CX-1/2)**: 업로드 수락·FinishPolicy 정상·session_id 물리 유지·집계 제외(rank_entry 부재). 확정 파이프라인(`SessionFinalizationService`)은 status≠OPEN/RUNNING이면 no-op — CANCELLED 미반응(기존 가드, 재검 완료). 결과조회 404·히스토리 `session_cancelled=true` 배지·rank/is_pb null.
- **승격 자격 게이트(PR-1/2/3)**: 평가 순서 404(크루/트랙)→403(비멤버/타인)→409(CLOSED)→409 COURSE_PROMOTION_INELIGIBLE(DNF·거리 하한). 타인 트랙=403(존재 누설 방지), 미완주·<1km=409. distance·start/finish는 refined에서 서버 재확정(PR-4).
- **PB 값 일치(HS-3)**: 유저×course_id 확정 세션 rank_entry FINISHED 최소 record_time_s — RankingPolicy PB 정의와 동일 값. 라이브 PB=확정 세션만(취소 제외).
- **`rank` 예약어 인용(HS-4/R-003 이월5)**: 히스토리 SQL `re.\`rank\`` 백틱. `\`user\`` 조인도 인용. validate 통과.
- **kapi 503 분기**: RealKakaoTokenVerifier 5xx/타임아웃/연결실패 → `KakaoUnavailableException` → AuthService → 503 AUTH_KAKAO_UNAVAILABLE. 401(토큰 자격)과 배타. 스텁은 kapi 미호출이라 503 미발생.
- **필드명 3자 대조**: `avg_pace_s_per_km`는 전역 SNAKE_CASE가 S+Per를 `avg_pace_sper_km`로 오변환하므로 `@JsonProperty` 고정(HistoryRecordResponse·PersonalBestResponse) — **flutter-dev 통지 대상**(ResultResponse와 동일 규약).

## 4. 테스트 (신규 4)

- `integration/HistoryPromotionCancelledHttpFlowTest` — 확정 PB·히스토리 rank/pb + CANCELLED 배지·결과404·승격 + 게이트(비멤버403·타인트랙403·DNF409).
- `integration/CoursePromotionDistanceGateTest`(@TestPropertySource min=100km) — 거리 하한 미달 409(외부화 증명).
- `user/adapter/out/kakao/RealKakaoTokenVerifierTest`(개정) — 5xx·타임아웃 → `KakaoUnavailableException`(기존 401 기대 2케이스를 503 경로로 정정).
- `user/application/AuthServiceKakaoUnavailableTest` — 예외→ErrorCode 경계(503 vs 401 배타).

## 5. 미규정·보류

- **미규정-A4(정제 초기값)**: O-M2C-4로 62 §0에서 확정 유지(50/12/3/30, 외부화). 도메인 상수 그대로 — application.yml 승격은 운영 튜닝 시.
- **범위 밖(명시)**: 리플레이 스냅샷(M3)·타인/크루 히스토리·거리대 PB(2차)·승격 GPS 공백 상한(v0.2는 거리 하한만, 픽스처 축적 후). C8 공유 픽스처 CI는 test 소관.
- **보류 없음** — 계약 모호·게이트 차단 0. 신규 계약 5종 전량 구현.

## 6. 신규/변경 파일 (backend/)

- **ranking**: application/{HistoryQueryService, view/HistoryRecordView, view/PersonalBestView, port/out/LoadHistoryPort}, adapter/{out/persistence/HistoryQueryAdapter, in/web/MeHistoryController, in/web/dto/HistoryRecordResponse, in/web/dto/PersonalBestResponse}. 변경: ResultQueryService·LoadResultPort·ResultQueryAdapter(+isCancelled → CANCELLED 404).
- **race**: application/{PromoteCourseCommand, CourseCommandService(+promoteCourse), port/out/PromotionSourcePort}, adapter/{out/persistence/PromotionSourceAdapter, in/web/CourseController(+promote), in/web/dto/PromoteCourseRequest}.
- **tracking**: TrackUploadService(+CANCELLED 수락).
- **user**: application/port/out/KakaoUnavailableException(신규), adapter/out/kakao/RealKakaoTokenVerifier(+503 경로), application/AuthService(+503 catch).
- **common/config**: ErrorCode(+COURSE_PROMOTION_INELIGIBLE 409·AUTH_KAKAO_UNAVAILABLE 503), application.yml(+promotion.min-distance-m).
