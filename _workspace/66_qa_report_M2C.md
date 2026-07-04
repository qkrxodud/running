# QA 7차 보고 — M2-C (결과 표면: 히스토리·PB·코스 승격·CANCELLED 보존·503) 3자 대조

> 작성: qa · 2026-07-05 · 기준: `63_backend_report_M2C.md`·`64_flutter_report_M2C.md`·`62_analyst_design_M2C.md`, 개정 계약 5종(history-api v0.1·course-api v0.2·track-api v0.1.2·auth-api v0.1.1·conventions v0.1.3)
> 실행: `./gradlew build` exit 0 (189 tests, 실 MySQL Testcontainers — M2-C 통합테스트 4종 포함) · `flutter test` 202 통과. 라이브 왕복은 자동 통합테스트로 갈음(수동 bootRun 미실행 — 아래 §5).

## 판정: PASS (경계면·불변식) — 차단 0 / 경고 1 / 참고 3

경계면 불일치·불변식 위반 0. 유일 경고는 결과 대기 화면 n/m 카운트 오도(R-009, UX·계약 갭 — 정합성 위반 아님).

---

## 1. 3자 대조 (계약 ↔ 서버 ↔ 클라 — 전부 일치)

### 1.1 history-api §1/§2
| 필드/규칙 | 계약 v0.1 | 서버 | 클라 | 판정 |
|---|---|---|---|---|
| records 필드 | track_record_id·session_id·course_id·course_name·scheduled_at·finish_status·rank?·record_time_s?·total_distance_m?·avg_pace_s_per_km?·is_pb·session_cancelled | `HistoryRecordResponse`(camelCase→SNAKE_CASE, `@JsonProperty("avg_pace_s_per_km")`) | `RecordHistoryItem.fromJson` 동일 키·nullable T? | 일치 |
| avg_pace 필드명(P46-2) | avg_pace_s_per_km | `@JsonProperty` 고정(S+Per 오변환 회피) | `json['avg_pace_s_per_km']` | 일치 |
| session_cancelled | bool | `"CANCELLED".equals(rs.status)` (`HistoryQueryAdapter:68`) | `json['session_cancelled'] as bool? ?? false` | 일치 |
| 키 생략(P46-1) | DNF·CANCELLED → rank/record_time_s/avg_pace 생략 | 전역 non_null + LEFT JOIN rank_entry null | `as T?` 안전 파싱 | 일치. `HistoryPromotionCancelledHttpFlowTest` 실 스택 검증 |
| finish_status enum | {FINISHED,DNF} (DNS 부재) | String "FINISHED"/"DNF"(`HistoryQueryAdapter:62`) | `FinishStatus`({FINISHED,DNF}) 재사용+unknown 폴백 | 일치 |
| personal-bests 필드 | course_id·course_name·distance_m·best_record_time_s·avg_pace_s_per_km·achieved_session_id·achieved_at | `PersonalBestResponse`(@JsonProperty avg_pace) | `PersonalBestItem.fromJson` 동일 | 일치 |

### 1.2 course-api §4 promote — 자격 게이트 code 배타
| 조건 | 계약 §4 | 서버(`CourseCommandService.promoteCourse`) | 클라 | 판정 |
|---|---|---|---|---|
| 크루 없음 | 404 | `findCrew` empty→NOT_FOUND(:83) | code 분기 | 일치 |
| 비멤버 | 403 | `!isActiveMember`→FORBIDDEN(:84-85) | 403/FORBIDDEN | 일치 |
| CLOSED 크루 | 409 CREW_CLOSED | `crew.closed()`→CREW_CLOSED(:87-88) | code 분기 | 일치 |
| 트랙 없음(본인) | 404 | `find` empty→NOT_FOUND(:90-91) | code 분기 | 일치 |
| 타인 트랙 | 403(존재 누설 방지) | `!ownerUserId.equals`→FORBIDDEN(:92-93) | 403 | 일치 |
| DNF·거리<1km | 409 COURSE_PROMOTION_INELIGIBLE | `!finished`/`<min`→INELIGIBLE(:95-102) | 409 INELIGIBLE code 분기 | 일치 |
| distance·좌표 | 서버 refined 재확정(PR-4) | refined 폴리라인 decode→`Course.create` 재계산(:103-118) | 응답 CourseDetail 소비 | 일치 |
- 평가 순서: crew-level(404→403→409) → track-level(404→403→409). 계약 "404→403→409" 축약과 정합. 크루 자격 확인 전 트랙 정보 미노출.

### 1.3 track-api v0.1.2 §3 CANCELLED → 404
- 계약: CANCELLED 세션은 RaceResult 미생성 → `404 NOT_FOUND`(RESULT_NOT_READY 아님).
- 서버 `ResultQueryService.getResult`: sessionExists→404 · isCrewMember→403 · **isCancelled→404** · findResult empty→409 RESULT_NOT_READY. 순서·코드 정합.
- 클라 `result_screen.dart:48-51`: `statusCode==404` → "결과가 없습니다(취소되었거나 존재하지 않아요)" 별도 메시지. RESULT_NOT_READY(409)는 `ResultPending`→대기화면. 배타 처리 일치.
- `HistoryPromotionCancelledHttpFlowTest:88` CANCELLED result→`isNotFound()` 실 스택 검증.

### 1.4 auth 503 vs 401
- 계약 auth-api §1: `503 AUTH_KAKAO_UNAVAILABLE`(kapi 장애, 재시도) / `401 AUTH_KAKAO_TOKEN_INVALID`(자격, 재로그인) 배타.
- 서버 `AuthService.login`: `KakaoUnavailableException`→503 / `KakaoTokenInvalidException`→401(:49-53). `ErrorCode.AUTH_KAKAO_UNAVAILABLE`=SERVICE_UNAVAILABLE. 배타.
- 클라 `login_screen.dart:47-52`: `kakaoUnavailable`→"잠시 후 다시 시도"(**재로그인 유도 없음**·루프 금지) / `kakaoTokenInvalid`→"인증 실패". code-only 분기, 배타.
- 검증: `RealKakaoTokenVerifierTest`(5xx/타임아웃→503 경로) + `AuthServiceKakaoUnavailableTest`(예외→ErrorCode 503 vs 401 배타).

---

## 2. 불변식

- **payload 격리(3차/4차 재확인)**: 히스토리 SQL(`HistoryQueryAdapter`) = track_record+race_session+course+race_result+rank_entry 조인, **track_payload 조인 0건**(:36-51). 결과 SQL(`ResultQueryAdapter`) 동일 0건. PB SQL 0건. 승격만 `PromotionSourcePort.refinedPolyline()` 전용 포트로 refined 접근(PR-6, 조회 어댑터 미주입 — 구조적 격리). ✅
- **CANCELLED 보존**: 업로드 수락(`TrackUploadService:86-87` OPEN|RUNNING|FINALIZING|CANCELLED 수락, COMPLETED/DRAFT 거부). session_id 물리 유지(삭제·NULL화 코드 없음). 집계 제외 = LEFT JOIN rank_entry null(CANCELLED은 race_result 부재). 히스토리 배지 노출·rank/is_pb null. ✅
- **승격 refined 전용·distance 서버확정**: `Course.create`가 refined 폴리라인으로 distance·start/finish 재확정(CO-B3/PR-4). track_record 저장값·클라값 불신. ✅
- **PB 완주만**: `findPersonalBests` = `rank_entry ... WHERE record_time_s IS NOT NULL`(확정 세션 완주만, CANCELLED/DNF 제외 — rank_entry 부재/null). 코스별 min. RankingPolicy 정의와 동일 값(HS-3). ✅
- **rank 예약어**: 히스토리·결과 SQL `re.\`rank\`` 백틱, `\`user\`` 인용(R-003 이월5). validate 통과. ✅

---

## 3. 클라 렌더 (결과 화면 계약 의미론)

- **P46-1 키부재 안전**: `ResultFormat.time(null)`→"--:--", `pace/distance(null)`→"-". DNS/DNF 항목 크래시 없음(테스트 박제). ✅
- **동률 공동순위**: 서버 산정 rank 그대로 렌더(1,1,3). rank==1→lime. ✅
- **PB 뱃지**: `entry.isPb` true만(`result_screen.dart:234`). ✅
- **하단 배치**: 서버 정렬(완주→DNF→DNS) 유지, 비완주 status muted. ✅

---

## 4. n/m 이슈 판정 (item 4) — **경고 (R-009)**, 계약 갭

**증상**: 결과 대기 화면 "전원 완료 대기 중 (n/m명)"이 **대기 구간 내내 0/m** 로 표시됨(오도).

**근거(증명적)**: `WaitingProgress.fromParticipants`(`result_format.dart:57`)는 done=FINISHED+DNF, total=FINISHED+DNF+STARTED. 그러나 서버는 participation 을 **세션 확정 시 일괄 전이**한다 — `SessionFinalizationService.doFinalize`만 `finalizeTo(FINISHED/DNF/DNS)`를 호출하고, `tryFinalizeIfAllUploaded`는 전원 업로드 전엔 no-op(`SessionFinalizationService:74`). 즉 개별 참가자가 업로드해도 participation 은 STARTED 로 남고, FINISHED/DNF 는 세션이 COMPLETED(=결과 Ready, 대기화면 종료)되는 **순간에만** 생긴다. 따라서 RESULT_NOT_READY(Pending) 구간에서 done 은 **항상 0** → 3명 중 2명 업로드해도 "0/3명".

**근본 원인**: 계약 갭 — 어떤 엔드포인트도 per-participant 업로드 여부를 노출하지 않는다(ParticipantView 는 participation status 만). 최종화 상태를 업로드 대리로 쓸 수 없다(최종화는 이진).

**부수 발견**: `result_screen_test.dart:117` n/m 테스트가 FINISHED2+STARTED1(=대기 구간에 **도달 불가능한** 상태) 픽스처로 "2/3"를 검증 → 결함을 가리고 false confidence 부여.

**심각도 = 경고**(차단 아님): 경계·정합성 위반 아님. 화면은 새로고침 시 정상 Ready 전환. 그러나 카운트가 실사용에서 항상 오도.

**권고**:
- ① 즉시 완화(flutter-dev): 오도성 "0/m" 카운트 표시 중단 — 수치 제거하고 "다른 참가자 기록 대기 중"만, 또는 m(참가자 수)만 표기. `result_screen_test.dart` 픽스처를 도달 가능한 Pending 상태로 정정.
- ② 근본(domain-analyst 제품 결정): 진짜 업로드 진행률이 제품 가치라면 세션 상세/결과 계약에 per-participant `has_uploaded`(또는 업로드 카운트) 필드 추가. 계약 확장 여부는 제품 결정 — QA는 확장을 **필요 조건이 아닌 옵션**으로 판단(대기 화면은 수치 없이도 성립).
- 레지스트리 R-009 OPEN 등록. 결정 후 재현 테스트/계약 확장이 장치.

---

## 5. 실행 검증

- `./gradlew build` exit 0 — 189 tests, 실 MySQL Testcontainers. M2-C 통합테스트 4종 green: `HistoryPromotionCancelledHttpFlowTest`(CANCELLED→결과404·session_cancelled 배지·is_pb·승격·게이트 비멤버403/타인403/DNF409·payload 조인 0), `CoursePromotionDistanceGateTest`(거리 하한 409), `AuthServiceKakaoUnavailableTest`·`RealKakaoTokenVerifierTest`(503 vs 401 배타).
- `flutter test` 202 통과.
- **라이브 왕복**: 별도 수동 bootRun+curl 미실행. 63 §1 라이브 곡선(업로드→취소→히스토리 배지→결과404→승격→게이트)이 위 통합테스트로 **자동화·바이트 단위 assert**됨 — 수동 curl보다 강한 영구 회귀선으로 갈음. sandbox(8081) 무접촉. docker 가용 — 필요 시 8080 수동 왕복 가능.

---

## 6. 이월

- **참고 P26-2 / C8 공유 픽스처**: history/result/track-me 실응답 바이트를 양쪽이 소비하는 단일 공유 픽스처 파일 여전히 미생성. 이번 회차에서 서버 실응답 필드셋 ↔ 클라 파싱이 실측 일치함은 확인(통합테스트 assert + 3자 대조). test-engineer 소관 — 종결 아님.
- **참고 미규정-A4**: 정제 파라미터(50/12/3/30) 도메인 상수 — 운영/사용자 승인 대기(63 §5).
- **참고 promote 존재 누설**: 타인 트랙(존재)→403 vs 부재→404 로 존재 구분 가능. **계약 §4 오류 목록이 명시적으로 이 구분을 규정**(본인 트랙 아니면 403·부재 404) — 서버가 계약을 정확히 구현. track_record_id 는 순차 int64라 저가치 프로브. QA 판단: 계약 정합, 결함 아님(명시만).
- **참고 실 카카오 503 재검**: dev 스텁은 kapi 미호출이라 503 미발생. 실 카카오 어댑터 활성(앱 키 확보) 시 503 경로 라이브 재검증 필요.
- **이월(범위 밖)**: M2-B 본체(진행 화면·트래커 실배선·리커버리·업로드 실패 UI), M3(리플레이·구간페이스·추월).
