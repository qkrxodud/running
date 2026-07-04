# 25 — 테스트 보고 (배치 B2: T1 CI + T2 상호운용·상태머신 골든)

> 작성: test-engineer · 2026-07-04 · 기준: `21_planner_plan_B2.md`(B2-T1/T2), `22_analyst_design_B2.md` §1.2·§2.4, `23_backend_report_B2.md` §3(이관 함수), `24_flutter_report_B2.md`, `docs/contracts/course-api.md`(1e5 + half-away-from-zero)
> 방법론: `golden-testing` 스킬(기대값은 계약·표준 알고리즘에서 도출 — 구현 역산 금지)

---

## 1. 완료 기준 결과

| 게이트 | 결과 |
|---|---|
| `flutter analyze` | ✅ 0 issues |
| `flutter test` (전체) | ✅ **117 passed** (기존 112 + tie 골든 5) |
| `./gradlew build` (전체, Testcontainers 라이브 포함) | ✅ **BUILD SUCCESSFUL — 99 tests, 0 failures** (기존 60 + 신규 39) |

CI와 동일 명령을 로컬에서 실행해 양쪽 green 확인(act 불요). docker 데몬 로컬 존재 → Testcontainers 통과.

---

## 2. T2 — 골든 케이스 (양쪽 결과)

### 2.1 폴리라인 상호운용 tie 종결 골든 (QA 이월 "폴리라인 정밀도" 최종 종결)
동일 골든 값을 **Dart·Java 양쪽에 문자 단위로 박제**:
- Dart: `app/test/core/geo/polyline_codec_test.dart` — 그룹 `상호운용 tie 종결 골든 (T2)` **5 케이스**
- Java: `backend/.../race/domain/PolylineCodecInteropTest.java` — `T2 tie 종결 골든` 블록 **5 케이스** (파일 7→12)

박제된 tie 벡터(±0.000005° = *1e5 = 정확히 ±0.5 IEEE754 tie → half-away-from-zero 강제):

| 입력 | 기대 문자열(양쪽 동일) | half-up 회귀 시 |
|---|---|---|
| `(+0.000005, 0)` | `A?` | `A?` (양수는 동일) |
| `(-0.000005, 0)` | `@?` | **`??`로 갈림 → red** |
| `(0, -0.000005)` | `?@` | `??` |
| `(-0.000005, -0.000005)` | `@@` | `??` |
| 자체 벡터 `[(0.000005,0.000005),(-0.00001,-0.00001),(0,0)]` | `AABBAA` | 첫 청크 어긋남 |

→ 서버가 `Math.round()`(half-up)로 회귀하면 음수 tie가 0으로 잘려 즉시 red. **음좌표 tie가 회귀 감지의 핵심 지점**. 현재 구현(Java `round1e5` 부호분리, Dart `num.round()`)은 둘 다 half-away → 전 케이스 green. Google 표준 벡터는 기존 seed에 이미 박제되어 중복 회피.

### 2.2 RaceSessionPolicy 상태머신 골든 (24셀 전수)
- Java: `backend/.../race/domain/RaceSessionPolicyMatrixGoldenTest.java` (신규) — 6상태 × 4명령 = **24셀 전수 파라미터화 + 커버리지 가드 1 = 25 tests**.
- 기대값은 설계 §2.4 매트릭스에서 도출(구현 역산 금지). `null`=불법 전이(`IllegalSessionTransitionException`). 셀 좌표(상태·명령)가 실패 메시지에 드러나도록 `@ParameterizedTest(name=...)`. 기존 seed `RaceSessionPolicyTest`(명령 축)와 상보 — 갭(전수 표) 보강.

### 2.3 GeoDistance 하버사인 골든
- Java: `backend/.../race/domain/GeoDistanceGoldenTest.java` (신규) — **9 tests**. 기대값은 하버사인 공식 + R=6,371,000m 손계산:
  - 적도 1°=111195m, 자오선 90°=10007543m, 반적도 180°=20015087m, 중위도(37.5°) 경도 1°=88216m(cos 수축), 계약 예제 start→finish=2567m, 서울 3점 누적=1448m, 경계(빈/단일/동일좌표=0).
- Dart 측 하버사인 함수 부재(거리는 서버 확정 — CO-B3) → GeoDistance 골든은 Java 단독(정당).

**신규 골든 합계**: Java 39개(폴리라인 tie 5 + 상태머신 25 + 하버사인 9), Dart 5개(폴리라인 tie).

---

## 3. 발견 버그

**없음.** 이관 함수 4종(PolylineCodec encode/decode, GeoDistance, RaceSessionPolicy, tie 반올림) 전부 계약·표준 알고리즘·설계 매트릭스와 일치. `docs/regressions.md` 신규 항목 없음.

- 순수성 반려: 없음. 이관 함수 4종 모두 IO·시계·랜덤 무개입(ArchUnit R-1 green) — 골든 가능.
- 미규정 skip: 없음.

---

## 4. T1 — CI 구성 요약

`.github/workflows/ci.yml`: push·PR(main) 트리거, **flutter job**(subosito/flutter-action 3.41.7 stable 고정 → pub get → analyze → test) + **backend job**(setup-java temurin 25 → gradle 캐시 → docker 확인 → `./gradlew build`, ubuntu 러너 docker로 Testcontainers 동작) 2잡, 실패 시 머지 차단(브랜치 보호에서 필수 체크 지정 전제), concurrency 취소 + 테스트 리포트 아티팩트 업로드.
