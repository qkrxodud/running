# 31 · 백엔드 환경 3종 분리 (dev / sandbox / prod) — 구현 보고

날짜: 2026-07-04 · 담당: backend-dev

## 요약
- `./gradlew build` **BUILD SUCCESSFUL** (전체 테스트 통과, 신규 환경 테스트 2종 포함).
- sandbox 스택 실기동 검증 완료: health 200 + 스텁 로그인 200 → `down -v`로 정리.
- dev(8080) bootRun 서버는 **미접촉** (java PID 43136 LISTEN 유지 확인).

## 환경 매트릭스 (구현 결과)
| 항목 | dev | sandbox | prod |
|------|-----|---------|------|
| 프로필 | local | sandbox(신설) | prod(명시화) |
| 기동 | bootRun + 로컬 MySQL | compose 풀스택 | compose 풀스택 |
| 앱/DB 포트 | 8080 / 3306 | 8081 / 3307 | 8080 / 3306 |
| DB명·볼륨 | runningcrew | runningcrew_sandbox · mysql-data-sandbox | runningcrew · mysql-data |
| compose 프로젝트명 | — | runningcrew-sandbox | runningcrew |
| JWT | 내장 dev 기본값 | env 필수(기본값 없음, fail-fast) | env 필수(기본값 없음, fail-fast) |
| 스텁 카카오 | ON | ON | OFF(빈 부재 fail-fast) |
| DevCourseSeeder | ON | ON | OFF |

## 변경 파일
1. `backend/src/main/resources/application.yml` — `sandbox` 프로필 블록 신설(jwt.secret 기본값 없음 → env 필수, 스텁·시더 ON), `prod` 프로필 블록 명시화(로그 INFO), local 주석에 sandbox 반영. 각 프로필 용도 주석 1줄.
2. `backend/src/main/java/com/runningcrew/user/adapter/out/kakao/StubKakaoTokenVerifier.java` — `@Profile({"local","dev"})` → `+"sandbox"`. Javadoc 갱신.
3. `backend/src/main/java/com/runningcrew/race/application/DevCourseSeeder.java` — `@Profile` 동일 확장. Javadoc 갱신.
4. `backend/docker-compose.yml` — prod 기준 정비: `SPRING_PROFILES_ACTIVE=prod`, `JWT_SECRET=${JWT_SECRET:?...}`(미설정 시 compose 기동 거부, 기본값 제거), top-level `name: runningcrew`. 8080/3306/mysql-data 유지, restart unless-stopped.
5. `backend/docker-compose.sandbox.yml` — **신설**. 앱 8081·MySQL 3307, DB `runningcrew_sandbox`, 볼륨 `mysql-data-sandbox`, 컨테이너 `*-sandbox`, `name: runningcrew-sandbox`, `SPRING_PROFILES_ACTIVE=sandbox`, `JWT_SECRET` env 전달. → prod와 포트·볼륨·DB·컨테이너·프로젝트명 전부 분리(동시 기동 가능, 충돌 0).
6. `backend/README.md` — **신설**. 3환경 매트릭스 표 + 각 기동 명령(dev bootRun / sandbox·prod compose) + fail-fast·헬스체크 안내.
7. `backend/src/test/java/com/runningcrew/config/SandboxProfileContextTest.java` — **신설**. sandbox 프로필 풀부팅(Testcontainers MySQL, 부모 싱글턴 재사용)에서 StubKakaoTokenVerifier·DevCourseSeeder 빈 존재 검증(= prod fail-fast가 sandbox 오작동 안 함 확인).
8. `backend/src/test/java/com/runningcrew/config/JwtSecretFailFastTest.java` — **신설**. 빈 시크릿 → 컨텍스트 로드 실패(IllegalStateException, "JWT_SECRET"), 유효 시크릿 → 성공. ApplicationContextRunner(MySQL 불요, 고속).

## 검증 결과
- 빌드: `./gradlew build` SUCCESSFUL. `config.*` 테스트 4건 통과.
- compose 문법: prod/sandbox 둘 다 `docker compose config` 통과.
- sandbox 실기동(JWT_SECRET 임시값): Flyway가 `runningcrew_sandbox`에 V1·V2 적용 → Tomcat 8080(내부)/8081(호스트) → `Started RunningCrewApplication` → DevCourseSeeder no-op(크루 없음).
  - `GET :8081/actuator/health` → 200 `{"status":"UP"}`
  - `POST :8081/api/v1/auth/login {"kakao_access_token":"stub:sandbox-verify-1"}` → 200, access_token 발급, user id=1 신규 생성(스텁 로그인 동작 확인).
  - `docker compose -f docker-compose.sandbox.yml down -v` 정리 → 8081·3307 해제 확인.
- dev 서버(8080 bootRun) LISTEN 유지 — 미접촉.

## fail-fast 정합성 확인
- sandbox/prod: application.yml에 `jwt.secret` 기본값 없음 → 베이스 `${JWT_SECRET:}`만 상속 → env 미주입 시 JwtTokenProvider 생성자에서 부팅 거부.
- prod: 스텁 카카오 빈이 없어 KakaoTokenVerifier 미주입 → 부팅 실패(의도). 실 카카오 어댑터(@Profile prod) 배선 시 해소.
- sandbox: 스텁 빈 존재 → 위 fail-fast가 오작동하지 않음(SandboxProfileContextTest로 회귀 고정).

## 주의사항 / 인계
- prod compose는 실 카카오 어댑터 배선 전까지 **의도적으로 부팅 실패**한다(스텁 유출 차단). prod 실기동 검증은 카카오 키 확보 후.
- dev는 별도 compose 없이 로컬 MySQL(3306) 전제 bootRun. 로컬 MySQL 미기동 환경이면 `docker compose up -d mysql`(prod의 mysql 서비스, DB runningcrew)로 띄워 재사용 가능.
- 실제 sandbox 운용 시 `JWT_SECRET`은 안전한 값(≥256bit)으로 호스트 env/`.env`에 보관. 검증에 쓴 임시 시크릿은 코드/문서에 하드코딩하지 않음.
