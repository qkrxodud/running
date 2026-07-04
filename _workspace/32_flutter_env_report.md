# 32 · Flutter 환경 3종(dev/sandbox/prod) 분리 — 클라이언트

## 요약
`AppConfig` 를 dev/sandbox/prod 3환경 인식으로 확장하고, 게이트 판단을 "prod 여부"
기준으로 재정리했다. 판단부를 순수 함수 `EnvGate` 로 분리해 3환경 전 케이스를
유닛테스트로 박제했다(`String.fromEnvironment` 는 빌드시 고정이라 기존 게이트는
테스트 불가였음). sandbox config 신설 + README 3환경 매트릭스/실행 명령 갱신.

## 검증 결과
- `flutter analyze`: **No issues found!** (0)
- `flutter test`: **All tests passed** (122, env_gate 신규 6 케이스 포함)

## 변경 파일
| 파일 | 변경 |
|------|------|
| `app/lib/app/env_gate.dart` | **신규** — 순수 Dart 게이트 함수(isDev/isSandbox/isProd/showDevTools/devLoginEnabled). test-engineer 골든 대상 |
| `app/lib/app/app_config.dart` | environment 3값 인식, isSandbox 추가, devLoginEnabled 게터 추가, showDevTools/devLoginEnabled 를 EnvGate 위임. DEV_LOGIN 플래그 상수화 |
| `app/lib/features/auth/login_screen.dart` | 로컬 `devLoginEnabled` const(직접 `bool.fromEnvironment`) 제거 → `AppConfig.devLoginEnabled` 단일 창구 참조. 미사용 `flutter/foundation` import 제거 |
| `app/config/sandbox.json` | **신규** — APP_ENV=sandbox, API_BASE_URL placeholder(http://192.168.0.0:8081), DEV_LOGIN=true |
| `app/config/README.md` | 3환경 매트릭스, 실행 명령(dev/sandbox/prod), adb reverse(dev), sandbox 실 IP 기입 안내, cleartext/debug 빌드 주의 |
| `app/test/app/env_gate_test.dart` | **신규** — dev/sandbox/prod × 스텁로그인·/spike 게이트 회귀 테스트 |

## `== 'dev'` 트랩 전수 수색 결과
게이트 로직에서 `env == 'dev'` 로 sandbox 를 차단하는 코드는 **없었다**. 기존
`isProd = environment == 'prod'`, `isDev = !isProd` 구조라 sandbox 도구 노출은
이미 열려 있었으나, isDev 를 엄밀히 `== 'dev'` 로 좁히는 순간 회귀할 위험이 있어
게이트를 `showDevTools`/`devLoginEnabled` 로 명시하고 `!= 'prod'` 로 고정했다.
`login_screen.dart` 는 `DEV_LOGIN` 을 직접 읽던 우회로가 있어 `AppConfig` 단일
창구로 흡수했다(스킬의 "String.fromEnvironment 직접 호출 금지" 규범 정합).

## 게이트 동작 표 (dev/sandbox/prod × 스텁로그인·/spike)
| 환경 | 빌드 | 스텁 로그인(devLoginEnabled) | /spike(showDevTools) |
|------|------|:---:|:---:|
| dev | debug | 허용 | 허용 |
| sandbox | debug | **허용** | **허용** |
| sandbox | release(비권장) | 허용* | 허용* |
| prod | release | 차단 | 차단 |
| prod | debug | 차단 | 허용(디버그 편의) |

\* sandbox release 도 게이트는 열리나(env != prod), cleartext 때문에 debug 배포가
권장. prod 는 debug 라도 스텁 로그인은 항상 차단(DEV_LOGIN 플래그가 실수로 true 여도
`!isProd` 안전장치가 막음).

## cleartext / network_security_config
요청대로 `network_security_config.xml` 은 **만들지 않았다**(release 에 cleartext
예외를 심으면 prod 보안 약화). 대신 README 에 "sandbox 는 debug 빌드로 배포,
카카오 키·도메인 확보 후 https 전환" 을 명시. Android debug 는 cleartext 기본 허용.

## test-engineer 인계 — 순수 Dart 함수
`lib/app/env_gate.dart`의 `EnvGate` 정적 함수 5종(모두 인자 주입형, 플랫폼 의존
없음). 골든 테스트 초안은 `test/app/env_gate_test.dart` 로 제공했으니 픽스처 확장
필요 시 이어받으면 된다.

## 실기기 수동 테스트 절차 (자동 검증 불가 항목)
1. **sandbox 접속**: 맥에서 `ipconfig getifaddr en0` 로 LAN IP 확인 → `config/sandbox.json`
   의 API_BASE_URL 을 `http://<그 IP>:8081` 로 교체. 서버를 8081 에서 기동.
   폰과 맥을 같은 Wi-Fi 에 두고 `flutter run --dart-define-from-file=config/sandbox.json`.
   → 로그인 화면에 "DEV 로그인 (스텁)" 폼이 보이고 스텁 로그인 성공하는지 확인.
2. **sandbox /spike 접근**: 설정 → 트래킹 스파이크 진입점이 노출되고 `/spike` 가
   `/` 로 리다이렉트되지 않는지 확인.
3. **prod release 차단 확인**: `flutter build apk --release --dart-define-from-file=config/prod.json`
   설치 → 로그인 화면에 DEV 로그인 폼이 **없고**, `/spike` 딥링크가 `/` 로 튕기는지 확인.
