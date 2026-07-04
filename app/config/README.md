# dev/prod 환경 설정 (B2-C4)

`--dart-define-from-file` 로 환경별 값을 주입한다. 모든 키는 `lib/app/app_config.dart`
(`AppConfig`)가 단일 창구로 읽는다 — 코드에서 `String.fromEnvironment` 직접 호출 금지.

## 빌드/실행

```bash
# dev (기본값과 동일 — 생략해도 dev)
flutter run --dart-define-from-file=config/dev.json
flutter build apk --debug --dart-define-from-file=config/dev.json

# prod
flutter build apk --release --dart-define-from-file=config/prod.json
```

## 키 (대기 = 빈 값, 발급 후 값만 채움)

| 키 | 용도 | 상태 |
|---|---|---|
| `APP_ENV` | `dev`/`prod` — dev 도구 게이트 | 확정 |
| `API_BASE_URL` | 서버 base URL | dev 확정 · prod는 도메인 대기(placeholder) |
| `DEV_LOGIN` | 스텁 로그인 노출 | dev=true · prod=false |
| `KAKAO_APP_KEY` | 카카오 로그인 | **대기**(M0 앱 키) |
| `NAVER_MAP_CLIENT_ID` | 네이버 지도 | **대기**(M0 Client ID). 확보 시 지도 placeholder → 실 SDK 교체 |

> 실제 운영 키는 저장소에 커밋하지 않는다(placeholder만 커밋). 발급 후 CI 시크릿
> 또는 로컬 비커밋 파일로 주입한다.
