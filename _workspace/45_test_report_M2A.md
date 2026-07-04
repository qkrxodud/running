# 45 — 테스트 보고 (M2-A: 순수 함수 4종 골든 경계 카탈로그 전수)

> 작성: test-engineer · 2026-07-04 · 기준: `golden-testing` 스킬(경계 카탈로그), `domain-model` 스킬, `42_analyst_design_M2A.md`(기대값 근거), `43_backend_report_M2A.md` §3(이관 시그니처·seed).
> 결과: `./gradlew test` **BUILD SUCCESSFUL — 175 tests(기존 126 + 신규 49), 0 fail, 1 skipped**(R-008 재현 테스트 `@Disabled`). 신규 골든 48 executed + 1 disabled.

---

## 1. 신규 산출물

**테스트(신규 5클래스, 49 메서드)** — 전부 순수 함수(IO·시계·랜덤 0):
- `tracking/domain/TrackRefinementServiceGoldenTest.java` (12)
- `tracking/domain/FinishPolicyGoldenTest.java` (18)
- `tracking/domain/TrackSegmentServiceGoldenTest.java` (5 + R-008 disabled)
- `ranking/domain/RankingPolicyGoldenTest.java` (9)
- `race/domain/SessionClosePolicyGoldenTest.java` (5)

**테스트 지원**: `tracking/domain/TrackTestFixtures.java` — 자오선 좌표 헬퍼(북쪽 이동 = R·Δlat 정확), 스펙 하버사인 독립 재구현(원시 기준선), 계약 shape JSON 로더.

**합성 픽스처(업로드 계약 shape, 계약 예시 겸용)** — `backend/src/test/resources/fixtures/tracks/synthetic/`:
- `completed_run_lingers_at_finish.json`(13점, FINISHED) · `shortcut_dnf_6pt.json`(6점, DNF) · `gap_run_completed.json`(13점, 공백 1개 FINISHED).
- `fixtures/tracks/README.md` — 규약 + **실주행 픽스처 슬롯(`real/`)** 마련(사용자 실기기 테스트 대기).

**기대값 도출 원칙**: 전량 설계 42·계획서 §4/§5.4 + 하버사인/등거리평면 공식에서 도출(구현 실행 역산 0). 스무딩 포함 픽스처 거리는 파이프라인 스펙(§3.1)을 파이썬으로 독립 재현해 박제(검토 승인 골든). 임계값은 파라미터 주입(하드코딩 0).

---

## 2. 카탈로그 커버리지 표 (항목별 기존seed / 신규 / 해당없음)

### FinishPolicy (18 신규)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| 3조건 각각 단독 미충족(다른 둘 충족) 3케이스 | 신규 | 조건1/2/3_단독_미충족_*_DNF |
| 임계 직전/직후 29.9/30.1m | 신규 | 반경_29m·31m (+정확히30m) |
| 임계 89.9/90.1% | 신규 | 거리_899_직전·901_직후 |
| 임계 79.9/80.1% | 신규 | 일치율_80퍼_직전(7/9=0.777) + 정확히80% |
| 등호 정확히 30m/90%/80% | 신규 | 반경_정확히30m·거리_정확히90퍼·일치율_정확히80퍼 |
| 도착 진입 후 계속 달림 → 최초 진입 시각 | 신규 | 진입후_계속달림_finishedAt은_최초 |
| 미진입 종료 DNF | 신규 | 조건1_단독_미충족 + 빈_트랙_DNF |
| DNF 기록·경로 보존 | 신규 | DNF여도_트랙_보존 |
| (추가) 빈 트랙 / GPS 공백 완주 / 픽스처 end-to-end | 신규 | 빈_트랙_DNF·GPS_공백있어도_완주·픽스처_완주/지름길 |

### TrackRefinement (12 신규)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| accuracy 초과 제거 (+등호 50 유지) | 신규 | accuracy_초과_제거·accuracy_등호_50은_유지 |
| 순간이동 점프 보정 | 신규 | 순간이동_점프_제거 |
| 정지 구간(그로스타임 — 삭제 금지) | 신규 | 정지구간_미삭제_그로스타임 |
| 정제 후 거리 < 원시 하버사인 | 신규 | 정제후_거리는_원시보다_작다(1000→900) |
| 빈 트랙·1점·전점 저품질 | 신규 | 빈_트랙·단일_포인트·전점_저품질 |
| 원시 불변성 | 신규 | 원시_불변성 |
| gap 30s 경계 29.9/30.1 (등호 30s=비공백) | 신규 | 공백_임계_경계_30초 |
| (추가) 픽스처 정제 골든·공백 식별 | 신규 | 픽스처_완주_트랙_정제·픽스처_공백_트랙 |

### TrackSegment (5 신규 + 1 R-008)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| 정확히 500m 등호(단일 구간) | 신규 | 정확히_500m_단일구간 |
| 마지막 미완 구간 | 신규 | 마지막_미완_구간(750m→[500,750] 250m) |
| 2점 미만 | 신규 | 단일_포인트_빈목록·빈_트랙_빈목록 |
| **정확히 배수(1000m) → 유령 구간 없음** | **신규(R-008 red, @Disabled)** | R008_정확히_배수거리는_유령_0m_구간을_만들지_않는다 |

### RankingPolicy (9 신규 — seed와 중복 회피)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| 3자 동률 1,1,1,4 | 신규 | 삼자_동률_1_1_1_4 |
| 2자 동률 후속(중간 동률) | 신규 | 중간_동률_1_2_2_4 |
| 2자 동률 1,1,3 | **기존 seed** | RankingPolicyTest.동률은_공동순위_다음_건너뜀 |
| 전원 DNF | 신규 | 전원_DNF |
| 참가 1명 | 신규 | 참가자_1명_완주 |
| PB 동일 기록=미갱신 | 신규 | PB_동일기록은_미갱신(==는 갱신 아님) |
| PB 첫완주·갱신·미갱신(<) | **기존 seed** | RankingPolicyTest.PB는_완주만 |
| DNF는 is_pb 항상 false (priorPb 있어도) | 신규 | DNF는_priorPb_있어도_false |
| DNS is_pb false·rank null | 신규 | DNS_is_pb_false |
| 동률 내부 정렬 결정적(userId) | 신규 | 동률_내부_userId_오름차순 |
| DNF/DNS 하단·완주→DNF→DNS 순 | **기존 seed** | RankingPolicyTest.DNF_DNS는_rank_null |

### SessionClosePolicy (5 신규 — seed와 중복 회피)
| 카탈로그 항목 | 상태 | 테스트 |
|---|---|---|
| deadline 정확히 도달 등호 | 신규 | deadline_정확히_도달_확정 (+1ms 직전 미확정) |
| STARTED 미업로드→DNF·REGISTERED→DNS 혼재 | **기존 seed** | SessionClosePolicyTest.최종화_STARTED미업로드… |
| 전원 업로드 OR deadline 트리거 | **기존 seed** | SessionClosePolicyTest 3케이스 |
| 전원 REGISTERED → 전원 DNS | 신규 | 전원_REGISTERED_전원_DNS |
| STARTED 없고 deadline 전 → 미확정 | 신규 | STARTED_없으면_deadline전_미확정 |
| 이미 최종상태(FINISHED/DNF/DNS) 보존 | 신규 | 이미_최종상태_보존 |

---

## 3. 발견 버그

**R-008 (버그, 재현 테스트 red 관측 후 @Disabled — 수정 미시행, 규칙 준수)**
- 증상: `TrackSegmentService.segments`가 **구간 길이의 정확한 배수** 총거리에서 유령 0m·0s 구간을 1개 더 생성. 500m 구간으로 1000m 주행 → 스펙상 2구간이어야 하나 3구간([1000,1000] 추가). exact 500m 는 1구간(정상) → exact-multiple 처리가 **부동소수 부호 의존적 비일관**.
- 원인: `int segmentCount=(int)Math.ceil(total/lengthM)` (`TrackSegmentService.java:39`)에서 하버사인 누적 `total`이 배수를 미소 초과(예: 1000.0000000001) → ceil +1. epsilon 없음.
- 실위험: 낮음(실주행 정수 배수 명중 불가, 리플레이 색상용 M3 소비). 그러나 exact-multiple 보장이 깨져 회귀선에 편입.
- 조치: `docs/regressions.md` R-008 OPEN 등록. 재현 테스트 `TrackSegmentServiceGoldenTest#R008_…`(red 관측 2026-07-04) `@Disabled`로 빌드 green 유지. **backend-dev 수정 후 @Disabled 제거 → green 확인 필수.** 수정 방향: ceil에 상대 epsilon 또는 total 정수 반올림 후 분할.

그 외 3함수(FinishPolicy·TrackRefinement·RankingPolicy·SessionClosePolicy)는 신규 48케이스 전량 green — 스펙 대비 편차 없음.

---

## 4. 카탈로그 미커버 잔여

- **FinishPolicy 지름길/다른길 서술 케이스**: "지름길 주파(거리 짧고 일치율 낮음)"·"다른 길로 완주(거리 충족·일치율 미달)"는 §4.3 원문. 본 스위트는 각 조건을 **단독 격리**(조건2 단독 = 지름길 거리부족, 조건3 단독 = 다른길 일치율부족)로 구현 — 복합 실패는 단독 격리의 합이라 커버로 간주. 실주행 지름길 트랙 유입 시 픽스처로 보강 예정.
- **TrackRefinement 실주행 픽스처**: `fixtures/tracks/real/` 슬롯만 마련(README). 실기기 원시 트랙은 **사용자 실기기 테스트(M2) 대기** — 정제 파라미터(미규정-A4: 50/12/3/30) 튜닝의 회귀선은 실주행 픽스처 축적 시 확정. 현재 합성 픽스처로 알고리즘 거동만 박제.
- **ReplaySnapshot/추월 계산**: golden-testing 스킬 카탈로그의 4번째 대상이나 M2-A 범위 밖(M3, `43_backend_report` §6). 순수 함수 미구현 상태라 대상 없음 — M3에서 착수.
- **미규정으로 skip한 케이스 없음**: M2-A 기대값은 설계 42가 전량 규정(등호 포함 여부·PB 비교 범위 등) → domain-analyst 질의 불요.
