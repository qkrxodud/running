# 05 — 테스트 엔지니어 보고 (배치 A: 순수 Dart 함수 경계 테스트)

> 작성: test-engineer · 날짜: 2026-07-04 · 대상: flutter-dev 통지 순수 함수 5종 (`app/lib/core/`)
> 기준: `04_flutter_report.md` §4, golden-testing 스킬(원칙 1 기대값 도출), domain-model 스킬
> 방침: 기존 유닛 테스트와 중복 금지 — **경계·특수 케이스만 추가**. 기대값은 계산·표준 문서에서 도출(구현 실행 결과 복사 금지).

## 0. 결과 요약

| 항목 | 결과 |
|---|---|
| `flutter test` | **All tests passed (61개)** — 기존 30 + 신규 31 |
| 발견 구현 버그 | **없음** |
| 순수성 반려 | **없음** (5종 모두 IO·시계·랜덤 없음, 시각 인자 주입 확인) |

## 1. 커버한 함수와 추가 케이스 수

| # | 대상 | 파일 | 신규 케이스 |
|---|---|---|---|
| 1 | `PolylineCodec.encode/decode` | `test/core/geo/polyline_codec_test.dart` | 8 |
| 2 | `SamplingPolicy.intervalFor` | `test/core/tracking/sampling_policy_test.dart` | 5 |
| 3 | `TrackBuffer.shouldFlush/drain` | `test/core/tracking/track_buffer_test.dart` | 5 |
| 4 | `BackoffPolicy` | `test/core/upload/backoff_policy_test.dart` | 6 |
| 5 | `UploadQueue` 전이 | `test/core/upload/upload_queue_test.dart` | 7 |

기존 파일에 그룹을 덧붙이는 방식으로 구조를 존중했다(신규 파일 생성 없음).

### 1.1 PolylineCodec — 왕복 정밀도 + 상호운용 골든 (8)
표준 알고리즘 문서에서 도출한 박제 골든(서버 Java 상호운용 기준):
- 원점 단일 점 → `"??"` (델타 0,0 = 63,63). 손계산 유도.
- 단일 점 `(38.5,-120.2)` → `"_p~iF~ps|U"`. 표준 3점 벡터의 첫 두 청크(절대 델타)와 일치 — Google 문서가 위도 38.5→`_p~iF`, 경도 -120.2→`~ps|U`를 명시.
- **음수 + 경도 -180 근처** `(-179.9832104)` → `"?\`~oia@"`. Google 문서 워크스루 대표 예제(`\`~oia@`)를 박제.
- 경도 +180 근처(179.99999), 남/서반구 음수 좌표(-33.86882,-151.20930) 왕복 1e-5.
- 6번째 이하 소수 자리 양자화(37.123456 등) 후에도 오차 ≤ 1e-5.
- 서울 한강 코스 수준 6점 트랙 전 구간 왕복 1e-5.
- `encode`↔`decode` 표준 벡터에서 상호 역함수(`encode(decode(golden))==golden`).

### 1.2 SamplingPolicy — 임계 직전/직후 (5)
정지 판정 속도 임계(0.5 m/s): 0.49(정지)·0.51(이동). 완화 유예(30s): 정확히 30초(>= 포함→idle)·29초(active)·지속 0(active). 기존은 0.5 경계와 31s만 커버 → 임계 양변·경계 등가값 보강.

### 1.3 TrackBuffer — 빈 버퍼/임계/drain 후 상태 (5)
빈 버퍼 drain=빈 목록·이후 flush 불필요, 임계-1은 미flush·정확히 임계는 flush, **age는 최초 진입 포인트 기준(이후 add로 갱신 안 됨)**, drain 반환 목록 불변(수정 시 UnsupportedError), drain 후 shouldFlush 재초기화.

### 1.4 BackoffPolicy — attempt/지수/상한 캡 (6)
- **상한 캡 경계 계산 유도**: base 2s·mult 2 → attempt 8 = 256s(캡 직전, 미캡), attempt 9 = 512s 계산→maxDelay(300s)로 캡. 캡이 켜지는 정확한 지점을 산식(`2·2^(n-1) ≥ 300`)으로 도출.
- multiplier=1.0(상수)·1.5(소수: 2→3→4.5s) 검증, 음수 attempt→zero, maxAttempts=0→첫 시도부터 shouldRetry false.

### 1.5 UploadQueue — 로컬 우선(C-5) 전이 (7)
- 존재하지 않는 id 전이 no-op(큐 불변), pending만 due(inFlight/succeeded/failed 제외), markInFlight 호출당 attempts+1, 실패→pending 재시도 후 due 재진입.
- **maxAttempts 소진 failed는 recordRef(데이터 참조) 보존 — 삭제 아님**.
- **purgeSucceeded는 succeeded만 제거**: failed+pending+inFlight 혼재 시 성공 확정이 없으면 아무것도 제거되지 않음 → "성공 전 데이터 제거 경로 부재" 명시.
- nextAttemptAt == now 경계는 due 포함.

## 2. 발견 버그

**없음.** 5종 모두 스펙(표준 문서·domain-model 규범·C-5 불변식)과 일치. 구현 코드는 수정하지 않았다.

## 3. 순수성 반려 목록

**없음.** 대상 5종 전부 시각을 인자 주입(now/stationaryFor)받고 IO·랜덤 없음 → 골든 테스트 가능. `no_platform_imports_test.dart`가 코어 레이어 플랫폼 import 0건을 이미 강제.

## 4. 특이사항 — Dart↔Java 반올림 상호운용 주의 (버그 아님, 관측 사항)

폴리라인 인코딩의 `(coord * 1e5).round()`에서 **Dart와 Java의 절반값(tie) 반올림 방향이 다르다**:
- Dart `double.round()`: 0에서 먼 쪽으로(`(-2.5).round() == -3`, `(2.5).round()==3`).
- Java `Math.round()`: +무한대 쪽으로(`Math.round(-2.5) == -2`, `Math.round(2.5)==3`).

→ **음수 좌표가 정확히 반(半)-마이크로도(예: scaled 값이 정수+0.5)일 때만** 양측 인코딩 문자열이 1 unit(1e-5) 어긋날 수 있다. 영향 평가:
- **실무상 미발생**: 실수 좌표가 `x*1e5`에서 정확히 `.5` tie를 만드는 경우는 부동소수점 특성상 거의 없다(테스트로 안정 재현 불가하여 케이스 미작성).
- **디코드 상호운용은 무영향**: 클라 인코딩 → 서버 디코딩 흐름에서 서버가 얻는 좌표는 항상 1e-5 이내. 계약상 서버가 같은 좌표를 재인코딩해 문자열 비교하는 경로는 없음.
- **권고(개발자·domain-analyst 참고)**: 서버 Java 구현이 인코딩도 수행한다면, tie 처리를 `Math.floor(x*1e5 + 0.5)` 또는 명시적 half-away-from-zero로 맞춰 완전 바이트 동일성을 확보하는 것을 배치 B에서 검토. 지금은 계약 위반이 아니므로 테스트 강제하지 않음.

## 5. 회귀 방지선 현황

신규 31케이스는 전부 계산/표준 문서 유도 골든이라 구현 변경 시 회귀를 즉시 검출한다. 폴리라인 표준 벡터(`_p~iF~ps|U_ulLnnqC_mqNvxq\`@`)와 문서 예제(`\`~oia@`)가 서버 Java 구현의 상호운용 계약 기준으로 박제됨.
