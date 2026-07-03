---
name: flutter-client
description: 러닝크루 앱 Flutter 클라이언트 구현 컨벤션 — 트래킹 계층 추상화(LocationTracker 등 3개 인터페이스), 순수 Dart 코어, 로컬 우선 저장·업로드 재시도, 적응형 GPS 샘플링, 클라이언트 상태머신, 패키지 선정, 리플레이 뷰어. app/ 디렉토리의 앱 코드 작성·수정·리뷰 시 반드시 읽을 것. 화면, 위치 추적, 지도, 로그인, 업로드 작업이 대상.
---

# Flutter 클라이언트 컨벤션 (Android 우선 → iOS 확장 전제)

도메인 규칙은 `domain-model` 스킬이 진실이다. 이 스킬은 클라이언트 구현 방법을 다룬다.

## 계층 구조

```
app/lib
├── core            # 순수 Dart — 플랫폼 채널 import 금지
│   ├── tracking    # 버퍼링, 적응형 샘플링 판단, 폴리라인 인코딩
│   ├── storage     # 로컬 우선 저장 (트랙, 업로드 큐)
│   ├── upload      # 업로드 재시도(지수 백오프), 큐 관리
│   └── model       # 도메인 모델, 계약 DTO
├── platform        # OS 종속 지점 — 인터페이스 + 구현체
│   ├── location    # LocationTracker ← AndroidForegroundTracker
│   ├── notification# NotificationService
│   └── permission  # PermissionService
└── features        # 화면 단위 (crew, course, race, replay, ranking, ...)
```

## 플랫폼 격리 — 3개 인터페이스

iOS 확장 시 구현체만 추가되고 상위 레이어는 무수정이어야 한다(계획서 §6). Android 전용 코드가 이 경계 밖으로 새면 그 시점에 iOS 비용이 급증하므로, 리뷰에서 최우선으로 본다.

1. **`LocationTracker`**: `start()/stop()/pause()`, 포인트 스트림(`Stream<TrackPoint>`), 상태. MVP 구현체는 `AndroidForegroundTracker` — Foreground Service(type: location) + 상시 알림. `ACCESS_BACKGROUND_LOCATION` 선언 금지(Play 심사 우회가 설계 의도).
2. **`NotificationService`**: 포그라운드 서비스 알림 vs (후일) iOS 로컬 알림.
3. **`PermissionService`**: 위치·알림 권한 플로우 — OS별 상이.

Android 전용 패키지(`flutter_foreground_task` 등)의 import는 구현체 파일 내부로만 한정한다.

## 패키지 선정 기준

양 플랫폼 지원 우선: `geolocator`(위치), `flutter_naver_map`(지도), `kakao_flutter_sdk`(로그인), `firebase_messaging`/`firebase_crashlytics`. 새 패키지 도입 시 iOS 지원 여부를 먼저 확인하고, Android 전용이면 platform/ 구현체 안으로만.

## 로컬 우선 원칙 (신뢰의 근간)

"서버 다운 = 결과 지연이지 유실이 아니다"를 코드로 보장한다:

- 트랙 포인트는 수신 즉시 로컬에 append (메모리 버퍼 + 주기 flush). 앱 강제 종료에도 마지막 flush까지는 생존해야 한다.
- 업로드는 완주 후 비동기 + **지수 백오프 재시도** (업로드 큐는 로컬 저장, 앱 재시작 시 재개).
- **업로드 성공 확인 전에 로컬 데이터를 삭제하는 코드를 절대 만들지 않는다.**
- '레이스 시작' 시 서버에 STARTED 신호 1회 — 실패해도 무시하고 진행(기록에 영향 없음).

## 클라이언트 상태머신

`READY → RUNNING → FINISHED_LOCAL → UPLOADED` — 서버 Participation 상태와 별개다. FINISHED_LOCAL(완주했으나 업로드 대기)이 존재하는 이유: 서버가 죽어도 완주는 성립해야 하기 때문. UI는 이 상태를 "업로드 대기 중"으로 표시하고 재시도를 노출한다.

## 트래킹 세부

- 샘플링 적응형: 주행 중 3~5초 → 정지 감지 지속 시 10초 → 재개 시 복귀 (배터리). 판단 로직은 core/tracking의 순수 Dart로 — 테스트 대상.
- 타임스탬프는 GPS 시각 우선 (기기 시계 오차 방지).
- 전송 형식: 인코딩 폴리라인 + 병렬 배열(timestamp/speed) — 계약(`docs/contracts/`) 기준.
- 기록 종료: 도착점 반경 진입 시각은 **서버가 확정**한다. 클라이언트는 원시 데이터를 보내는 쪽 — 로컬에서 기록을 잘라내지 않는다.
- 온보딩에 배터리 최적화 예외 안내 포함(삼성 앱 자동 종료 대응).

## 서버 통신

- DTO는 `docs/contracts/`의 계약에서만 도출한다. 서버 코드를 추측해 필드를 만들지 않는다 — 모호하면 domain-analyst에게.
- JSON은 snake_case (계약 기준). 파싱 실패는 조용히 삼키지 말고 Crashlytics 로깅.
- 앱 시작 시 `GET /app-version`으로 강제 업데이트 판단.
- 업로드 실패율 로깅 — 운영 관측 항목이다.

## 리플레이 뷰어

- 입력은 ReplaySnapshot 하나 (서버 사전 계산: 추월 지점 포함). 클라이언트는 렌더링만: 마커 보간 애니메이션, 배속, 시간축 슬라이더, 추월 마킹, 구간 페이스 색상.
- 상대 시각(t=0 정렬) 데이터임을 전제로 구현 — 절대 시각 가정 금지.
- `schema_version` 확인 후 미지원 버전은 "앱 업데이트 필요" 안내.

## 완료 기준

`flutter analyze` + `flutter test` 통과. 백그라운드 트래킹 등 실기기 검증 필요 항목은 자동 검증 불가를 명시하고 수동 절차(화면 끈 채 1시간 기록 등)를 보고서에 남긴다.
