# 러닝크루 백엔드 (Spring Boot 3.5 + Java 25, 헥사고날)

앱↔서버 API 계약은 `../docs/contracts/`, 도메인 규범은 개발 하네스 스킬(`domain-model`, `backend-hexagonal`)이 진실이다.

## 환경 매트릭스 (dev / sandbox / prod)

| 항목 | dev | sandbox | prod |
|------|-----|---------|------|
| Spring 프로필 | `local` | `sandbox` | `prod` |
| 기동 | `bootRun` + 로컬 MySQL | compose 풀스택 | compose 풀스택 |
| 앱 포트 | 8080 | **8081** | 8080 |
| MySQL 포트 | 3306 | **3307** | 3306 |
| DB명 / 볼륨 | `runningcrew` / (로컬) | `runningcrew_sandbox` / `mysql-data-sandbox` | `runningcrew` / `mysql-data` |
| compose 프로젝트명 | — | `runningcrew-sandbox` | `runningcrew` |
| JWT 시크릿 | 내장 dev 기본값 | **env `JWT_SECRET` 필수** (fail-fast) | **env `JWT_SECRET` 필수** (fail-fast) |
| 스텁 카카오 로그인 | ON | ON (앱 키 확보 전 크루원 테스트용) | OFF (fail-fast) |
| DevCourseSeeder | ON | ON | OFF |
| restart 정책 | — | unless-stopped | unless-stopped |

- **sandbox / prod에는 `jwt.secret` 기본값이 없다.** env `JWT_SECRET`(≥256bit, HS256)를 주입하지 않으면 `JwtTokenProvider`가 부팅을 거부한다(fail-fast) — 개발용 시크릿이 운영/외부 테스트에 새는 것을 구조로 차단.
- **prod는 실 카카오 어댑터(`@Profile("prod")`) 배선 전까지 의도적으로 부팅에 실패한다** — 스텁이 운영에 유출되지 않도록 `KakaoTokenVerifier` 빈 부재로 fail-fast. 카카오 앱 키 확보 후 실 어댑터를 추가하면 해소된다.

## 기동 명령

### dev (로컬 개발 — bootRun)
로컬에 MySQL(3306, DB `runningcrew`)이 떠 있는 상태에서:
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
# 앱 http://localhost:8080 — 내장 dev JWT 시크릿 + 스텁 카카오 로그인
```

### sandbox (크루원 실기기 테스트 — 풀스택 compose)
```bash
JWT_SECRET=<임의의 256bit 이상 시크릿> \
  docker compose -f docker-compose.sandbox.yml up -d --build
# 앱 http://localhost:8081 / MySQL localhost:3307 (DB runningcrew_sandbox)
# 스텁 로그인 예: POST /api/v1/auth/login  { "kakao_access_token": "stub:tester-1" }
docker compose -f docker-compose.sandbox.yml down          # 중지(볼륨 유지)
docker compose -f docker-compose.sandbox.yml down -v       # 중지 + DB 볼륨 삭제
```

### prod (운영 — 풀스택 compose)
```bash
JWT_SECRET=<운영 시크릿> docker compose up -d --build
# 앱 http://localhost:8080 / MySQL localhost:3306 (DB runningcrew)
docker compose down
```

> sandbox와 prod 스택은 포트·볼륨·DB명·컨테이너명·compose 프로젝트명이 전부 분리되어 있어
> **같은 호스트에서 동시에 기동**할 수 있다(충돌 0).

## 검증 / 헬스체크

```bash
./gradlew build                                   # 컴파일 + 전체 테스트(Testcontainers MySQL 필요)
curl http://localhost:8081/actuator/health        # sandbox health → {"status":"UP"}
curl http://localhost:8080/actuator/health        # prod health
```

- `GET /app-version` 및 `GET /actuator/health`는 인증 불요.
- 환경 분리 관련 테스트: `SandboxProfileContextTest`(sandbox 컨텍스트 로드 + 스텁/시더 빈 존재),
  `JwtSecretFailFastTest`(시크릿 미주입 시 부팅 실패).
