# 75 — 테스트 보고 (M3-A: 리플레이 순수 함수 3종 골든 확장 + 공유 픽스처 배선)

> 작성: test-engineer · 2026-07-05 · 기준: `golden-testing` 스킬(경계 카탈로그), `domain-model` 스킬, `72_analyst_design_M3A.md`(기대값·추월 부호반전 정의), `73_backend_report_M3A.md` §3(이관 시그니처·seed — 중복 금지), `74_flutter_report_M3B.md`(뷰어 수기 픽스처 — 교체 대상).
> 결과: `./gradlew test` **BUILD SUCCESSFUL — 222 tests, 0 fail, 0 skipped** · `flutter test` **243 pass** · `flutter analyze` 0.

---

## 1. 신규 산출물

**골든/정책 테스트(신규 4클래스, 24 메서드)** — replay 3종은 IO·시계·랜덤 0(순수):
- `replay/domain/ReplayMergerGoldenTest.java` (6)
- `replay/domain/OvertakeCalculatorGoldenTest.java` (7)
- `replay/domain/PaceColorizerGoldenTest.java` (6)
- `race/application/SessionReminderServicePolicyTest.java` (5) — 서비스는 포트 의존(비순수)이라 **clock 주입 + 인메모리 fake 포트**로 결정적 정책 골든(멱등·메시지 계약). 시각 윈도 SQL 경계는 어댑터 소관(통합 seed 커버)임을 테스트 주석에 명시.

**공유 픽스처(C8 패턴 확장 · A10)**:
- `docs/contracts/fixtures/replay_snapshot_v1.json` — **서버 생성으로 전환**. `SharedContractFixtureTest` 에 replay 케이스 추가(실 `ReplayMerger`·`OvertakeCalculator` 출력 → 서버 payload 어셈블러 키 미러 → `ReplaySnapshotResponse` 직렬화). 뷰어 테스트(`app/test/core/model/replay_dtos_test.dart`)를 수기 File 로드에서 **공유 로더(`test/support/contract_fixtures.dart`) 소비로 교체**.
- 기대값은 전량 설계 72·계획서에서 도출(구현 역산 0). replay 좌표는 자오선(하버사인 정확)으로 cum_dist 손계산.

---

## 2. 카탈로그 커버리지 표 (기존seed / 신규 / 해당없음)

### ReplayMerger (6 신규)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| 시작 시각 다른 두 트랙 t=0 정렬 | **기존 seed** | ReplayMergerTest.t0_상대시각_정렬 |
| 단일 참가자 | 신규 | 단일_참가자 |
| DNF 조기 종료 트랙 | 신규 | DNF가_가장_길면_그_끝이_duration(DNF endMs=마지막 프레임) |
| is_gap 경계(gap 임계 직전/직후) | 신규(위임 해석) | 복수_공백_endIndex_표기 · 공백_없으면_전부_false. **주**: gap 임계(30s) 초과 판정은 TrackRefinement 소관(M2-A 골든 완료), ReplayMerger는 GpsGap.endIndex→is_gap 매핑만 담당 |
| cum_dist가 refined 기반(원시 금지, RP-4) | 신규 | cum_dist는_refined_하버사인(자오선 100/300m 정확) |
| 빈 frames | 신규 | 빈_frames_참가자 |

### OvertakeCalculator (7 신규)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| 교차 1회 | **기존 seed** | OvertakeCalculatorTest.부호_반전이면_추월_1건 |
| 재역전 N건 순차 | 신규 | 재역전은_순서대로_2건(at_dist 오름차순·passer 방향 교대) |
| 무추월(범위 겹침) | 신규 | 전구간_앞서면_무추월 |
| 동시 도달(비이벤트) | **기존 seed** | OvertakeCalculatorTest.동시_도달은_추월_아님 |
| DNF 도달 범위 밖 | **기존 seed** | OvertakeCalculatorTest.DNF_공통범위_밖은_미발생 |
| DNF 범위 안 추월(대칭 보강) | 신규 | DNF_범위_안_추월_발생 |
| 3인 이상 쌍별 계산 | 신규 | 삼인_쌍별_계산 |
| 출발 직후 역전 | 신규 | 출발_직후_역전 |
| 범위 미교집합 | 신규 | 범위_미교집합_이벤트_없음(한쪽 cum 0) |

### PaceColorizer (6 신규)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| 버킷 경계 등호(240) | **기존 seed** | PaceColorizerTest.페이스_버킷_경계_매핑 |
| 각 경계 정확값(300·360·420) | 신규 | 각_경계_정확값 |
| 경계 직전값(299·359·419) | 신규 | 경계_직전값 |
| 극단 페이스 | 신규 | 극단_페이스(0→0·9999→4) |
| 전 구간 동일 페이스 | 신규 | 전구간_동일_페이스 |
| 빈 세그먼트 | 신규 | 빈_세그먼트 |
| 파라미터 주입(하드코딩 아님) | 신규 | 커스텀_경계_주입 |

### SessionReminder 스케줄러 정책 (5 신규)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| reminder_notified_at 멱등 | 신규 | 멱등_재발송_금지 · 혼재_미발송만_발송 |
| 발송 메시지 계약(딥링크 §10) | 신규 | 발송_메시지_계약(runningcrew://session/{id}) |
| 발송 시각 경계(clock) | 신규(위임) | clock과_lead를_포트에_위임. **주**: scheduled_at≤now+lead 의 SQL 판정은 `SessionReminderAdapter`(비순수) — 통합 seed `SessionReminderHttpFlowTest` 커버 |
| 임박 없음 | 신규 | 임박_없으면_무발송 |

### 공유 픽스처 배선 (5)
| 항목 | 상태 |
|---|---|
| `replay_snapshot_v1.json` 서버 직렬화==파일 가드 편입 | 완료 — `SharedContractFixtureTest` 카탈로그(3→4파일), 역방향 가드·요구 케이스 실측(participants 3·overtakes≥1·is_gap·**DNF finish_time_ms 생략**·display_names 탈퇴 조인) |
| 뷰어 테스트 수기 픽스처 → 공유 파일 소비 교체 | 완료 — `replay_dtos_test.dart` 가 `loadContractFixture` 소비, authoritative 값(6프레임·추월 B→A·finish_time_ms 생략)으로 갱신 |
| drift 양방향 red 실증 | 완료 — `passer_user_id` 변조 시 서버 가드 red + 뷰어 테스트 red(복원 후 green) |

---

## 3. 발견 버그

**제품 버그 0.** replay 순수 함수 3종·SessionReminder 정책 신규 24케이스 전량 green — 설계 72 대비 편차 없음.

**픽스처 생성 주의점(버그 아님, 방법론 기록)**: 리플레이 payload 픽스처를 **도메인 record 직접 직렬화**로 만들면 전역 SNAKE_CASE가 `ReplaySegmentColor.paceSPerKm` → `pace_sper_km` 로 **오변환**(결과·히스토리 `avg_pace_s_per_km` 와 동일 함정)하여 실 서버(`ReplayGenerationService.segmentJson` 의 명시 키 `pace_s_per_km`)와 어긋난다. → 픽스처 생성을 서버 어셈블러의 **명시 키를 미러**하도록 교정(실 파이프라인 스키마는 통합 `ReplaySnapshotHttpFlowTest` 가 별도 실증). 서버 payload 는 하드 어셈블이라 실사용 오변환 위험 없음 — 픽스처 진위 확보를 위한 생성기 교정.

**flutter-dev 수기 픽스처 교정(P26-2 효과)**: 종전 수기 `replay_snapshot_v1.json` 은 DNF에 `"finish_time_ms": null`(명시적 null)을 담았으나, 서버 실 직렬화는 NON_NULL로 **키 자체 생략**. 공유 픽스처 전환으로 이 drift가 자동 교정됐고, 뷰어 파서(`json['x'] as int?`)는 키 부재·null 모두 null 처리라 무해함을 재확인.

---

## 4. 카탈로그 미커버 잔여

- **SessionReminder 시각 윈도 SQL 경계**: `findDueSessionIds` 의 `now < scheduled_at ≤ now+lead` 판정은 어댑터(SQL) — 순수 함수 아님. 통합 seed `SessionReminderHttpFlowTest` 가 커버. 서비스 레이어 정책(멱등·메시지·위임)만 골든화(비순수 우회 테스트를 짜지 않음 — 역할 원칙).
- **실주행 리플레이 픽스처**: `fixtures/tracks/real/` 슬롯(M2-A 마련) 유지 — 실기기 스냅샷 유입 시 회귀선 편입. 현재 합성(자오선·손계산)으로 알고리즘 거동 박제.
- **뷰어 성능(60fps·10명×600pt)·딥링크 실수신**: 실기기 대기(74 §4) — 자동 골든 불가 영역, 범위 밖.
- domain-analyst 질의로 skip한 케이스 없음(M3-A 기대값은 설계 72가 전량 규정: 부호반전·동시도달·재역전·DNF 범위·버킷 경계).

---

## 5. 이월 상태(선행 배치)

- **R-008(M2-A)**: `TrackSegmentService` 정확 배수 거리 유령 0m 구간 — `@Disabled` 재현 테스트 + `docs/regressions.md` OPEN, backend-dev 수정 대기(본 배치 무관, 상태 유지).
- **R-009(C8)**: 공유 픽스처 CI 승격 CLOSED — 본 배치가 replay 픽스처로 동일 패턴 확장(양방향 red 가드 실증).
