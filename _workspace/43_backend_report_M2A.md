# 43 — 백엔드 구현 보고 (M2-A: 서버 심장부 — Tracking·Ranking·마감 전이)

> 작성: backend-dev · 2026-07-04 · 기준: `41_planner_plan_M2.md`(A1~A11), `42_analyst_design_M2A.md`, `docs/contracts/track-api.md v0.1`·`conventions.md v0.1.2`
> 선행 패턴: B2(`23_backend_report_B2.md`) race 컨텍스트. 빌드: `./gradlew build` ✅ (126 tests). 라이브 곡선: 격리 MySQL8 + bootRun(local) 왕복 curl ✅.

---

## 1. 빌드·라이브 곡선 결과

- **`./gradlew build` BUILD SUCCESSFUL** — 126 tests 전량 통과. Testcontainers(MySQL 8) 위 Flyway V1+V2+**V3** 적용 → `ddl-auto=validate` 부팅 성공(신규 엔티티 5종 매핑 검증 = R-003 이월5 증명: `rank_entry.rank`가 `globally_quoted_identifiers`로 안전 인용).
- **라이브 곡선**(격리 mysql:8 @3399, bootRun local @8080 — 공유 3306/샌드박스 8081·3307 무접촉): Flyway `Successfully applied 3 migrations ... v3`. 스텁 2명 로그인 → 크루·초대·조인 → 코스(distance_m 1588 서버확정) → 세션 open/register×2/start×2(RUNNING) → 확정 전 결과 `409 RESULT_NOT_READY` → **합성 트랙 2개 업로드**: 리더 완주(`FINISHED` dist 1548 time 600s gaps 0), 멤버 지름길(`DNF` finished_at null dist 715) → **전원 업로드로 자동 확정(AFTER_COMMIT 동기)** → 세션 `COMPLETED` → 결과 조회: 리더 rank 1·`is_pb true`·`avg_pace_s_per_km 388`, 멤버 DNF 하단(rank 생략=null·dist 715 보존·is_pb false). 멱등(동일 키 200 / 다른 내용 409 `TRACK_ALREADY_UPLOADED`), 확정 후 업로드 409 `SESSION_STATE_INVALID`. DB 확인: track_record 2행(client_upload_id·gps_gap_count 채움), track_payload 2행(raw+refined 분리 저장), rank_entry 2행(rank 1개 non-null).
  - **정리**: bootRun 종료(8080 해제). 격리 컨테이너 `m2a-live-mysql`은 **stop 완료**했으나 sandbox 권한이 `docker rm`을 거부 — 정지 상태(무해). 필요 시 `docker rm m2a-live-mysql`.

## 2. API·순수 함수 수

**신규 API 5개**(track-api v0.1 / 전부 auth required):
| 메서드 | 경로 | 컨텍스트 | 비고 |
|---|---|---|---|
| POST | `/api/v1/sessions/{id}/track` | tracking | 업로드·정제·판정·저장. 멱등(client_upload_id). 201/200 |
| GET | `/api/v1/sessions/{id}/track/me` | tracking | 내 트랙 요약(블롭 미포함). 미업로드 404 |
| GET | `/api/v1/sessions/{id}/result` | ranking | 결과·순위. 미확정 409 RESULT_NOT_READY, 비멤버 403 |

**순수 함수 4종**(도메인 패키지, IO·시계·랜덤 0 — ArchUnit R-1 green):
1. `TrackRefinementService.refine(List<TrackPoint>, RefinementParams) → RefinedTrack` — accuracy 필터→점프 보정→이동평균→정제 후 거리→GPS 공백. 파라미터 외부화(50/12/3/30).
2. `FinishPolicy.judge(RefinedTrack, CourseShape, FinishParams) → FinishJudgment` — 3조건 AND(30m/0.90/0.80/50m 외부화), finished_at=도착 반경 최초 진입 시각, DNF도 거리 보존. **코스 이탈 검증 일원화 지점**.
3. `TrackSegmentService.segments(RefinedTrack, SegmentParams) → List<TrackSegment>` — 500m 구간 페이스(외부화). M2 결과 API 미노출(refined_payload 내장).
4. `RankingPolicy.rank(List<RankingInput>) → List<RankedEntry>` — 오름차순·동률 공동순위 건너뜀(1,1,3)·DNF/DNS 하단·PB(완주만·유저×코스 과거 세션 최소기록).

보조 순수 함수: `SessionClosePolicy.shouldFinalize/finalize`(race.domain, clock 주입), `TrackPolylineCodec`(1e5), `TrackGeo`(하버사인·점-폴리라인).

**확정 파이프라인(A9·A10, M2 동기)**: `TrackUploaded`(AFTER_COMMIT, `REQUIRES_NEW`) → 전원 업로드 재평가 → `RaceCompleted`(→ranking 동기 산정·저장) → `ResultFinalized`(→race COMPLETED). 컨텍스트 결합은 domain.event로만(R-2). `SessionCloseScheduler`(@Scheduled, clock 주입, idempotent)로 deadline 경로.

**계약 대비 편차 0.** track-api §0~§3 전량 구현. `avg_pace_s_per_km`는 전역 SNAKE_CASE가 연속 대문자를 오변환하여 `@JsonProperty`로 계약 필드명 고정.

## 3. test-engineer 이관 — 순수 함수(골든 대상)

전부 `com.runningcrew.{tracking|ranking|race}.domain`(프레임워크 무관). backend가 seed 테스트로 핵심 거동을 박제(아래) — 경계 카탈로그·실주행 픽스처 확장이 test-engineer 소관. **임계값은 파라미터 주입(하드코딩 금지)**, 거리 기대값은 **정제 후 좌표 기준**(원시 하버사인 금지, FR-1).

1. **TrackRefinementService.refine** — 입력 `List<TrackPoint>`(lat,lng,tsMillis,speed,accuracy,altitude?) + `RefinementParams(accuracyMaxM,maxSpeedMps,smoothingWindow,gapThresholdS)`. 골든 요청: accuracy>50 제거 / 순간속도>12 점프 제거 / 창3 스무딩 / **정지구간 미삭제(그로스타임 FR-2)** / Δt>30s 공백 메타(gps_gap_count). 예시: 21점 직선(37.5000, 127.0000+i·0.0009) 30s 간격 → 정제거리 ≈1548m, gaps 0.
2. **FinishPolicy.judge** — 입력 `RefinedTrack` + `CourseShape(polyline, finish, distanceM)` + `FinishParams(30,0.90,0.80,50)`. 골든: 정상완주 / 지름길(②거리 DNF) / 다른길(③일치율 DNF) / 도착 미진입(①DNF) / **경계 등호**(정확히 30m·90%·80%·50m — 반경·코리도 ≤, 거리·일치율 ≥) / GPS 공백 완주. 예시: 21점+도착 3점 → FINISHED, finished_at=도착 반경 최초 진입점 ts. 앞 11점만 → DNF(finished_at null, dist 715).
3. **TrackSegmentService.segments** — 입력 `RefinedTrack` + `SegmentParams(500)`. 골든: 등호 경계(정확히 500m)·마지막 미완 구간·2점 미만 빈목록.
4. **RankingPolicy.rank** — 입력 `List<RankingInput(userId, ResultStatus, recordTimeS?, priorPbTimeS?)>`. 골든: 동률 1,1,3 / DNF·DNS 하단(완주→DNF→DNS) / PB(첫완주·갱신 true, 미갱신·DNF/DNS false) / 전원 DNF. 예시는 seed `RankingPolicyTest`.
5. (보조) **SessionClosePolicy** — clock 주입 deadline 전이(전원 업로드 OR now≥deadline), STARTED미업로드→DNF·REGISTERED→DNS·WITHDRAWN 제외. seed `SessionClosePolicyTest`.

backend seed 테스트(참고·회귀선): `ranking/domain/RankingPolicyTest.java`, `race/domain/SessionClosePolicyTest.java`, `race/domain/RaceSessionPolicyMatrixGoldenTest.java`(24→**36셀**로 확장 — FINALIZE/COMPLETE 추가), 통합 `integration/TrackUploadFinalizationHttpFlowTest.java`(정제·판정·순위 end-to-end 실증).

## 4. qa 경계면 (3자 대조·불변식)

- **track_payload 블롭 격리(TR-3, A7/A8 후 재검증)**: 조회 3경로(track/me·result·확정 파이프라인) SQL에 `track_payload` 조인 **0건**. 구조 강제: `LoadTrackRecordAdapter`·`ResultQueryAdapter`·`TrackResultQueryAdapter`는 payload 리포지토리 미주입, `TrackPayloadJpaRepository`는 저장(`SaveTrackAdapter`) 전용. 라이브 DB 확인 시 track_payload 2행(별도 테이블) 저장·조회 미동반.
- **enum 3자 대조**: 서버 `finish_status`{FINISHED,DNF}·결과 `status`{FINISHED,DNF,DNS}·`processing_status`{PROCESSED} ↔ track-api §1·§3 ↔ 클라 DTO(M2-B/C). ranking `ResultStatus`는 participation 최종값 부분집합. `@Enumerated(STRING)` 고정.
- **payload 표현 규약**: 폴리라인 1e5(course-api 동일, `TrackPolylineCodec`)·`timestamps` epoch millis(conventions §9)·`client_meta` 3키(TK-4 서버 재검증). 라이브 왕복 문자 단위 일치.
- **오류코드**: `TRACK_ALREADY_UPLOADED`(409)·`TRACK_PAYLOAD_INVALID`(400)·`TRACK_ARRAY_LENGTH_MISMATCH`(400)·`TRACK_TOO_LARGE`(413)·`RESULT_NOT_READY`(409) 매핑 확인(통합 테스트 커버).
- **상태머신**: FINALIZE(OPEN·RUNNING·FINALIZING 재진입)·COMPLETE(FINALIZING만). **FINALIZING cancel=409**(SS-1, O-M2-2) — 매트릭스 골든 36셀 박제.
- **컨텍스트 경계(R-2)**: tracking·ranking은 타 컨텍스트 클래스 미참조 — 크루/세션/코스/트랙 접근은 네이티브 SQL 포트(`TrackUploadSupportPort`·`TrackResultQueryPort`·`PriorPbPort`·`LoadResultPort`·`SessionSchedulingPort`). 결합은 domain.event만(TrackUploaded·RaceCompleted·ResultFinalized). ArchUnit R-1~R4 green.
- **avg_pace 필드명**: `avg_pace_s_per_km`(계약) — SNAKE_CASE 오변환 회피 `@JsonProperty` 고정. **flutter-dev 통지 대상**.

## 5. 설계·구현 판단 노트 (편차 없음, 명시만)

- **V3 마이그레이션 추가**: track_record에 `client_upload_id`(멱등 판별)·`gps_gap_count`(요약 — 상태/결과 조회가 블롭 미로드하도록) 컬럼. finish_status는 **파생**(finished_at 유무)이라 컬럼 미추가.
- **확정 트랜잭션**: AFTER_COMMIT 리스너는 커밋 완료 후 실행 → `Propagation.REQUIRES_NEW`로 새 트랜잭션 강제(기본 REQUIRED는 "no transaction in progress" — 실제 발견·수정). 중첩 동기 이벤트(RaceCompleted→ResultFinalized)는 이 트랜잭션 내에서 처리(M2 동기 확정). 리플레이 생성(M3)만 별도 AFTER_COMMIT+@Async 예약.
- **미래 타임스탬프 허용치**: `now + 1일` 초과만 거부(경미한 시계 편차 허용). 외부화 여지 표기.
- **정제 후 스무딩과 완주**: 라이브에서 도착점 3점(정지) 부가로 마지막 스무딩 포인트가 반경 30m 내 유지됨을 실증(합성 픽스처 설계 주의점 — test-engineer 인계).

## 6. 미규정·보류

- **미규정-A4(정제 초기값 승인)**: `accuracy_max_m=50 / max_speed_mps=12 / smoothing_window=3 / gap_threshold_s=30` — 계획서 미규정, 설계 제안값으로 **외부화·구현**. `RefinementParams.defaults()`·`FinishParams.defaults()`·`SegmentParams.defaults()`에 상수. **사용자/운영 승인 요망**(승인 후 application.yml 바인딩으로 승격 가능 — 현재는 도메인 상수).
- **범위 밖(미구현, 명시)**: 리플레이 스냅샷 생성(M3, ResultFinalized까지만 발행)·구간 페이스/추월 결과 API 노출(M3)·개인 기록 히스토리·CANCELLED 개인기록 보존(M2-C)·정제 파라미터 config 바인딩(승인 대기).
- **환경**: 격리 `m2a-live-mysql` 컨테이너 stop 완료·rm 권한 거부로 잔존(무해). sandbox(8081/3307)·prod mysql(3306) 무접촉.
- **보류 없음** — 계약 모호·발급물 게이트로 막힌 항목 0. track-api v0.1 전량 구현.
- **QA 5차 후속(2026-07-04) — R-006 CLOSED**: TK-3 본문 바이트 상한(≤8 MiB) 배선. `TrackUploadSizeInterceptor`(Content-Length 프리체크, 디코딩·버퍼링 前 차단) + `track.max-request-bytes` 외부화. preHandle 예외가 `GlobalExceptionHandler` 경유 `{code,message}` 413 TRACK_TOO_LARGE(컨테이너 하드차단과 달리 계약 shape 유지). 포인트 수(20k)와 쌍 가드. 테스트 `TrackUploadSizeLimitTest`(상한 512B override·상한 직후 413, red→green).
- **QA 5차 후속(2026-07-04) — R-007 CLOSED(잔여: M2-B 클라 분기·3자 대조)**: 업로드/start 권한 경계 403·409 배타(평가 순서 404→403 비멤버→409 상태/미등록→409 중복→400/413, 계약 track-api v0.1.1·session-api v0.2.1). `TrackUploadService.upload`에 ACTIVE 멤버십 가드 삽입(`TrackUploadSupportPort.isActiveCrewMember` native SQL 신설). `RaceSessionCommandService.start`는 이미 정합(검증만·무변경). 테스트 `TrackUploadMembershipGuardTest`(업로드·start 비멤버403/멤버미등록409 + /me 403, red→green).

## 7. 신규/변경 파일 (backend/)

- **tracking.domain**: TrackCoord, TrackPoint, TrackGeo, TrackPolylineCodec, RefinementParams, GpsGap, RefinedTrack, TrackRefinementService, FinishParams, CourseShape, FinishStatus, FinishJudgment, FinishPolicy, SegmentParams, TrackSegment, TrackSegmentService, TrackRecord, InvalidTrackPayloadException, event/TrackUploaded
- **tracking.application**: TrackUploadService, TrackUploadCommand, UploadOutcome, TrackQueryService, view/TrackRecordSummary, port/out/{LoadTrackRecordPort, SaveTrackPort, TrackUploadSupportPort}
- **tracking.adapter**: in/web/{TrackController, dto/TrackUploadRequest, dto/TrackRecordResponse}, out/persistence/{TrackRecordJpaEntity, TrackPayloadJpaEntity, *JpaRepository, SaveTrackAdapter, LoadTrackRecordAdapter, TrackUploadSupportAdapter}
- **ranking.domain**: ResultStatus, RankingInput, RankedEntry, RankingPolicy, event/ResultFinalized
- **ranking.application**: RankingFinalizationListener, ResultQueryService, view/ResultView, port/out/{PriorPbPort, SaveRankingResultPort, LoadResultPort}
- **ranking.adapter**: in/web/{ResultController, dto/ResultResponse}, out/persistence/{RaceResultJpaEntity, RankEntryJpaEntity, *JpaRepository, SaveRankingResultAdapter, PriorPbAdapter, ResultQueryAdapter}
- **race.domain**(변경/추가): SessionCommand(+FINALIZE,COMPLETE), RaceSessionPolicy, RaceSession(+finalizeSession/complete/isTerminal), Participation(+finalizeTo), ParticipantClose, ParticipantOutcome, SessionClosePolicy, event/RaceCompleted
- **race.application**: SessionFinalizationService, TrackUploadedListener, ResultFinalizedListener, SessionCloseScheduler, port/out/{TrackResultQueryPort, SessionSchedulingPort}, ParticipationRepository(+findBySessionId)
- **race.adapter.out.persistence**: TrackResultQueryAdapter, SessionSchedulingAdapter, Participation*(+findBySessionId)
- **common**: SchedulingConfig(@EnableScheduling), error/ErrorCode(+5), resources/db/migration/V3__add_track_upload_id.sql
- **test**: ranking/domain/RankingPolicyTest, race/domain/SessionClosePolicyTest, race/domain/RaceSessionPolicyMatrixGoldenTest(36셀), integration/TrackUploadFinalizationHttpFlowTest
