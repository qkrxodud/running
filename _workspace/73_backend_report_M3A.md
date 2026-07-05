# 73 — 백엔드 구현 보고 (M3-A 리플레이 스냅샷 파이프라인 + M3-C 서버측)

> 작성: backend-dev · 2026-07-05 · 기준: `71_planner_plan_M3.md`(A1~A10·M3-C), `72_analyst_design_M3A.md`(스냅샷 스키마 v1), `docs/contracts/replay-api.md v0.1`·`conventions.md v0.1.4`(§10 딥링크)
> 선행: M2-A/B/C 종결. 빌드: `./gradlew build` ✅ **199 tests**. 라이브 곡선(2명 확정→자동 READY→표시명 조인→재생성 멱등→알림 1회) ✅.

---

## 1. 빌드·라이브 결과

- **`./gradlew build` BUILD SUCCESSFUL — 199 tests**(M3 신규 6 테스트 클래스). Testcontainers MySQL8 + Flyway V1~**V4**(reminder_notified_at) + `ddl-auto=validate` 부팅 성공(replay_snapshot 엔티티 매핑 검증). ArchUnit R-1~R4 green(replay는 projection — 6컨텍스트 R-2 루프 밖, tracking.domain 순수함수 재사용 정당).
- **라이브 곡선**(격리 mysql@3399 + bootRun local@8080, **sandbox 8081/prod 3306 무접촉**): 업로드 리더 FINISHED·멤버 DNF → 전원 업로드 자동 확정 → **ResultFinalized AFTER_COMMIT @Async → 스냅샷 자동 READY**(task-1 스레드) → 조회 `display_names={1:리더,2:멤버}`(조인) · payload schema=1·participants=2·duration_ms=600756·frames(FINISHED 24·DNF 11)·DNF finish_time_ms 생략 → 재생성(admin 202) → 최신 READY(snapshot_id 2, 동일 3542 bytes = 멱등) → **`notification_sent`=1**(REPLAY_READY·deep_link `runningcrew://replay/1`·recipients 2, **재생성 후 미재발** RP-12) → `replay_viewed` 구조화 로그(viewed_within_24h=true, A9) → admin 토큰 없음 403 · 비멤버 403. 정리 완료.

## 2. 구현 요약

**신규 API 3개**:
| 메서드 | 경로 | 인증 | 비고 |
|---|---|---|---|
| GET | `/api/v1/sessions/{id}/replay` | 크루 멤버 | status별(READY=payload+display_names·GENERATING/FAILED=상태만·미생성 404). 조회 로깅(A9) |
| POST | `/api/v1/admin/sessions/{id}/replay/regenerate` | **admin(X-Admin-Token)** | 삭제→최신 스키마 재생성 멱등(RP-10). 202 |

**파이프라인(A4·A5·A6)**: `ResultFinalizedReplayListener`(AFTER_COMMIT + @Async, RP-9) → `ReplayGenerationService.generate`(REQUIRES_NEW): GENERATING 저장→순수 병합·추월·색상→payload 조립→READY(payload)/예외 시 **FAILED+원인 로그**(RP-8), 2MiB 상한 초과 FAILED(RP-13), 최초 READY만 알림 게이트 발송(RP-12). `ReplayRegenerationService`(admin): deleteBySession(자체 tx 커밋)→generate. `@EnableAsync` 추가.

**payload 격리(RP-1)**: refined 접근은 `ReplaySourcePort`(track_record⋈track_payload native)만 — 순위/결과/히스토리 조회 어댑터엔 미주입(격리 유지). `ReplayQueryPort`(조회 보조)·`ReplaySnapshotRepository`는 track_payload 미접근. 컨텍스트 직접 호출 0(이벤트/전용 포트만, RP-2).

**M3-C 서버측**: `NotificationSender` 포트 + `StubNotificationSender`(구조화 로그, Firebase 시 어댑터 교체). "리플레이 열림"=최초 READY·`replay_notified_at` 원자적 check-and-set(RP-12). **세션 리마인더**: `SessionReminderScheduler`(@Scheduled)→`SessionReminderService.sendDueReminders(now)`(clock 주입)·`reminder_notified_at` 멱등·딥링크 `runningcrew://session/{id}`(§10). V4 마이그레이션 추가.

**표시명 조인(RP-3)**: payload는 user_id만, 조회 시 `ReplayQueryAdapter.displayNames`가 nickname 조인(탈퇴 `status=WITHDRAWN`→"탈퇴한 러너"). `schema_version=1`(외부화), 뷰어 미지 상위버전 게이트는 클라(M3-B).

## 3. test-engineer 이관 — 순수 함수 3종(골든 대상)

전부 `com.runningcrew.replay.domain`(IO·시계·랜덤 0, tracking.domain VO 재사용). seed 테스트로 핵심 거동 박제(ReplayMergerTest·OvertakeCalculatorTest·PaceColorizerTest) — 경계 카탈로그·실주행 픽스처 확장이 test-engineer 소관.

1. **`ReplayMerger.mergeToRelativeTimeline(List<ReplayTrackInput>, ColorParams) → MergedTimeline`** — 각자 t=0 상대시각(gps_time−started_at) 정렬·cum_dist(refined 하버사인, RP-4)·is_gap(GpsGap endIndex)·DNF finish_time_ms=null(RP-6)·duration_ms=전 참가자 최대 상대 종료. 예: A(시작100000·완주200000·3점·gap@2), B(시작105000·DNF·2점) → A.frames[0].t_ms=0·frames[2].is_gap=true·finish_time_ms=100000, B.finish_time_ms=null, duration_ms=100000. 골든: 시작 상이 정렬·DNF 조기종료·GPS공백·단일참가.
2. **`OvertakeCalculator.computeOvertakes(List<ReplayParticipant>) → List<Overtake>`** — T_u(d)=cum_dist 선형보간 도달시각, sign(T_A−T_B) 반전=추월(passer=뒤→앞), t_ms=max(T_A,T_B). 정렬(at_dist↑·passer↑·passed↑). v1 노이즈 필터 없음. 예: A(500m@100,1000m@400)·B(500m@200,1000m@300) → B가 A 추월 1건 at_dist∈(500,1000). 골든: **동시도달=이벤트 아님**·**재역전=순서대로 N건**·**DNF 공통범위 밖 미발생**·범위 미교집합.
3. **`PaceColorizer.colorize(List<TrackSegment>, ColorParams) → List<ReplaySegmentColor>`** — 페이스→버킷(기본 경계 [240,300,360,420]: <240→0 … ≥420→4). 예: pace 200→0·240→1(경계 등호)·330→2·500→4. 골든: 버킷 경계 정확값·마지막 미완 구간·전 구간 동일 페이스.

보조 seed: `SessionReminderService`(clock 주입 멱등)·통합 `ReplaySnapshotHttpFlowTest`(end-to-end)·`SessionReminderHttpFlowTest`.

## 4. qa 경계면

- **track_payload 격리 3차 재검증(RP-1)**: 신규 조회 경로(GET replay)·display_names·finalized_at 조회는 payload 미접근. 승격(M2-C)·리플레이 생성만 전용 포트로 refined 접근 — 순위/결과/히스토리 어댑터 여전히 미주입(구조적). 라이브 조회 SQL에 track_payload 부재.
- **상태·enum 3자 대조**: `ReplayStatus`{GENERATING,READY,FAILED}·`finish_status`{FINISHED,DNF}(payload). GENERATING/FAILED 응답 `@JsonInclude(ALWAYS)`로 명시적 null(계약 §1 리터럴). **payload 내 `finish_time_ms`는 non_null 생략**(DNF=키 부재=null, 결과 API 패턴과 동일 — flutter-dev/QA 주의).
- **딥링크(§10)**: REPLAY_READY→`runningcrew://replay/{id}`·SESSION_REMINDER→`runningcrew://session/{id}`. `data.deep_link` 단일 필드.
- **멱등**: 재생성 스냅샷 동일 bytes(순수함수)·최신=created_at max. FCM 세션당 1회(replay_notified_at)·리마인더 세션당 1회(reminder_notified_at) 원자적 UPDATE.
- **admin 게이트**: `/api/v1/admin/**` JWT 화이트리스트 + `X-Admin-Token` 대조(미설정=admin 비활성 403). prod 노출 제한.
- **A9 조회 로깅**: `replay_viewed`(session·user·viewed_at·finalized_at·viewed_within_24h) — user_id만(익명 파생, RP-14).

## 5. 보류·범위 밖

- **공유 픽스처 A10/C8**: 대표 스냅샷 `docs/contracts/fixtures/replay_snapshot_v1.json` 존재(뷰어 소비용). 서버 생성 바이트↔뷰어 파싱 교차 CI 배선은 test-engineer(SharedContractFixtureTest 확장) 소관 — 통합 테스트가 생성 payload 스키마(schema_version·participants·frames·is_gap·overtakes·segments·DNF finish_time_ms 생략) 실증으로 게이트 준비 완료.
- **미규정(진행은 제안값)**: 추월 노이즈 필터(v1 전량 기록 — 설계 §9)·admin 실인증 방식(운영 토큰 최소 구조)·조회 로깅 저장소(구조화 로그 우선). 색상 버킷 경계 [240,300,360,420]는 제안값(외부화 `ColorParams.defaults()`).
- **게이트 뒤(미구현)**: 실 FCM 발송(어댑터 교체)·클라 수신·뷰어 렌더(M3-B). RewardGrant 생성은 본 배치 범위 밖(M3-C Reward 별도).
- **보류 없음** — 계약 모호·발급물 차단 0. replay-api v0.1 전량 구현.

## 6. 신규/변경 파일

- **replay.domain**(순수): ReplayTrackInput·ReplayFrame·ReplaySegmentColor·ReplayParticipant·Overtake·ColorParams·MergedTimeline·PaceColorizer·ReplayMerger·OvertakeCalculator.
- **replay.application**: ReplayGenerationService·RefinedTrackParser·ResultFinalizedReplayListener·ReplayQueryService·ReplayRegenerationService·view/ReplaySnapshotView·port/out/{ReplaySnapshotRepository·ReplaySourcePort·ReplayQueryPort·ReplayNotificationGate}.
- **replay.adapter**: in/web/{ReplayController·AdminReplayController·dto/ReplaySnapshotResponse}·out/persistence/{ReplaySnapshotJpaEntity·JpaRepository·PersistenceAdapter·ReplaySourceAdapter·ReplayQueryAdapter·ReplayNotificationGateAdapter}.
- **notification**: domain/NotificationMessage·application/port/out/NotificationSender·adapter/out/StubNotificationSender.
- **race**(M3-C): application/{SessionReminderService·SessionReminderScheduler}·port/out/SessionReminderPort·adapter/out/persistence/SessionReminderAdapter.
- **common**: web/{AdminAuthInterceptor·WebMvcConfig(+interceptor)}·SchedulingConfig(+@EnableAsync). user/JwtAuthenticationFilter(+admin 화이트리스트). resources/db/migration/V4·application.yml(+replay·reminder·admin).
- **test**: replay/domain/{ReplayMergerTest·OvertakeCalculatorTest·PaceColorizerTest}·integration/{ReplaySnapshotHttpFlowTest·SessionReminderHttpFlowTest}.
