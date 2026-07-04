# dev/sandbox/prod 환경 설정 (B2-C4)

`--dart-define-from-file` 로 환경별 값을 주입한다. 모든 키는 `lib/app/app_config.dart`
(`AppConfig`)가 단일 창구로 읽는다 — 코드에서 `String.fromEnvironment`/`bool.fromEnvironment`
직접 호출 금지. 게이트 판단(스텁 로그인·`/spike`)은 순수 함수 `lib/app/env_gate.dart`
(`EnvGate`)로 분리돼 있고 규칙은 **"prod 여부" 기준**이다(아래 매트릭스).

## 환경 매트릭스

| 항목 | dev | sandbox | prod |
|------|-----|---------|------|
| config 파일 | `config/dev.json` | `config/sandbox.json` | `config/prod.json` |
| `APP_ENV` | `dev` | `sandbox` | `prod` |
| base URL | `http://localhost:8080` (adb reverse 전제) | `http://<LAN IP>:8081` (placeholder) | `https://api.<도메인>` (placeholder) |
| 프로토콜 | http (localhost) | **http (cleartext)** — debug 빌드로만 배포 | https |
| 빌드 타입 | debug | **debug** (cleartext 때문) | release |
| DEV(스텁) 로그인 | 허용 | **허용** (카카오 키 전까지 크루원 테스트) | 차단 |
| `/spike` 라우트 | 허용 | 허용 | 차단 |

> 게이트는 `environment != 'prod'` 로 넓게 열린다 — `== 'dev'` 로 좁게 비교하면
> sandbox 가 의도치 않게 차단되므로 금지. `EnvGate` 순수 함수에 3환경 전 케이스
> 테스트가 박제돼 있다(`test/app/env_gate_test.dart`).

## 빌드/실행

```bash
# dev (기본값과 동일 — 생략해도 dev). 실기기/에뮬레이터 공통 터널 필요:
adb reverse tcp:8080 tcp:8080
flutter run --dart-define-from-file=config/dev.json

# sandbox (LAN — 맥/홈서버가 8081 에서 대기 중이어야 함, 같은 Wi-Fi)
#  ※ cleartext http 라 debug 빌드로만 배포한다(아래 sandbox 주의 참조).
flutter run --dart-define-from-file=config/sandbox.json
flutter build apk --debug --dart-define-from-file=config/sandbox.json

# prod (release)
flutter build apk --release --dart-define-from-file=config/prod.json
```

## sandbox 실 IP 기입 안내

`config/sandbox.json` 의 `API_BASE_URL` 은 placeholder(`http://192.168.0.0:8081`)다.
서버를 띄운 맥/홈서버의 실제 LAN IP 로 교체한다.

```bash
# 맥에서 LAN IP 확인 (Wi-Fi 기준)
ipconfig getifaddr en0        # 예: 192.168.0.42
```

교체 예: `"API_BASE_URL": "http://192.168.0.42:8081"`. 폰과 서버가 **같은 Wi-Fi**
여야 접속된다. IP 는 각자 네트워크마다 다르므로 실 IP 는 커밋하지 않는다(placeholder 유지).
JSON 은 주석을 못 넣으므로 이 안내가 sandbox base URL 의 주석 역할을 한다.

## sandbox 주의 — cleartext / 빌드 타입

sandbox 는 도메인·TLS 확보 전 단계라 `http`(cleartext)로 LAN IP 에 붙는다.
Android debug 빌드는 cleartext 가 기본 허용되므로 **sandbox 는 반드시 debug 빌드로 배포**한다
(`flutter run` 또는 `flutter build apk --debug`). release 빌드는 cleartext 를 차단하므로
sandbox 를 release 로 만들면 네트워크가 막힌다.

`android/app/src/main` 에 `network_security_config.xml` 는 만들지 않는다 — release 에
cleartext 예외를 심으면 prod 보안이 약해진다. 카카오 키·도메인 확보 후 sandbox 도
https 로 전환하면 이 제약이 사라진다.

## 키 (대기 = 빈 값, 발급 후 값만 채움)

| 키 | 용도 | 상태 |
|---|---|---|
| `APP_ENV` | `dev`/`sandbox`/`prod` — 게이트 기준 | 확정 |
| `API_BASE_URL` | 서버 base URL | dev 확정 · sandbox LAN IP 기입 · prod 도메인 대기 |
| `DEV_LOGIN` | 스텁 로그인 노출 | dev=true · sandbox=true · prod=false |
| `KAKAO_APP_KEY` | 카카오 로그인 | **대기**(M0 앱 키) |
| `NAVER_MAP_CLIENT_ID` | 네이버 지도 | **대기**(M0 Client ID). 확보 시 지도 placeholder → 실 SDK 교체 |

> 실제 운영 키·LAN IP 는 저장소에 커밋하지 않는다(placeholder만 커밋). 발급 후 CI 시크릿
> 또는 로컬 비커밋 파일로 주입한다.
