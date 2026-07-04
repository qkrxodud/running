# 03 — 백엔드 구현 보고 (배치 A: 골조 + 계약)

> 작성: backend-dev · 날짜: 2026-07-04 · 기준: `01_planner_plan.md`(A-B1~A-B6), `02_analyst_design.md`(§1~§2), 계약 `docs/contracts/`(conventions.md, app-version.md)
> 범위: 백엔드 프로젝트 부트스트랩 + 헥사고날 골조 + V1 스키마 + 시간대 규약 + Docker Compose + 첫 계약 구현(`GET /api/v1/app-version`).

---

## 0. 빌드 결과 (완료 기준)

- **`./gradlew build` → BUILD SUCCESSFUL.** 테스트 4개 통과(0 실패/0 에러).
- 컴파일 산출물 바이트코드 **major version 69 = Java 25** 확인.
- 툴체인: Gradle 9.1.0(Java 25 정식 지원) + `foojay-resolver-convention` 0.10.0. JDK 25가 로컬에 있어(자동 감지된 OpenJDK 25 loom, `~/Library/Java/...`) foojay 자동 다운로드 없이 툴체인이 이를 사용했다. 실행 JVM은 JDK 21이나 **툴체인이 컴파일/테스트를 JDK 25로 강제**했다.
- 환경 요구사항 기록: 이 머신 기본 `java`는 21이다. 빌드는 Gradle 툴체인(languageVersion=25)이 격리 처리하므로 개발자 로컬 `JAVA_HOME`이 21이어도 무방. JDK 25가 아예 없는 CI에서는 foojay가 자동 다운로드한다(auto-download Enabled 확인).

---

## 1. 구현 내역 (계획 항목 대비)

| 항목 | 상태 | 비고 |
|---|---|---|
| A-B1 Spring Boot 3.5.6 + Java 25 + 헥사고날 골조 | 완료 | 6 컨텍스트 × 4 레이어 + replay + common. 빈 패키지는 `package-info.java`로 고정(25개) |
| A-B2 Docker Compose (Spring Boot + MySQL 8) | 완료 | `restart: unless-stopped`, MySQL 볼륨·초기 DB·healthcheck·타임존 UTC. 멀티스테이지 Dockerfile |
| A-B3 Flyway + V1__init.sql | 완료 | 설계 §2 그대로 17개 물리 테이블(§2.14가 plan+item 2개). TIMESTAMP(6) UTC, enum VARCHAR, PK=FK 1:1, rank_entry FK RESTRICT |
| A-B4 시간대 규약 + 직렬화 | 완료 | JVM UTC(`main`), MySQL 세션 UTC(JDBC `connectionTimeZone`+`hibernate.jdbc.time_zone`), Jackson Instant→ISO-8601(Z) |
| A-B6 GET /app-version | 완료 | 계약 app-version.md 준수. 200/400/404 매핑. 슬라이스 테스트 4개 |
| application.yml | 완료 | 로컬 프로필(MySQL localhost, env 오버라이드), snake_case, actuator health 노출, 콘솔 로그(UTC) |

> A-B5(계약 문서 초안)는 domain-analyst가 이미 `docs/contracts/`에 작성 완료 → 이번 배치는 **계약 소비/구현**(app-version)만.

### 생성 구조
```
backend/
├── build.gradle.kts / settings.gradle.kts / gradle.properties / gradlew(+wrapper)
├── Dockerfile / docker-compose.yml / .dockerignore / .gitignore
└── src/main/java/com/runningcrew/
    ├── RunningCrewApplication.java        # JVM UTC 고정
    ├── user|crew|race|tracking|ranking|reward/{domain,application,adapter.in.web,adapter.out.persistence}/  # 빈 골조(package-info)
    ├── replay/                            # 프로젝션 골조
    └── common/
        ├── TimeConfig                     # Jackson Instant UTC 직렬화
        ├── error/{ApiError,ErrorCode,ApiException,GlobalExceptionHandler}  # 계약 §4 {code,message}
        ├── web/PageResponse               # 계약 §6 페이지 래퍼
        └── appversion/{Platform,AppMinVersion,AppMinVersionRepository,AppVersionService,AppVersionResponse,AppVersionController}
    └── src/main/resources/{application.yml, db/migration/V1__init.sql}
```

---

## 2. 계약 대비 구현 API

| 계약 | 엔드포인트 | 구현 | 검증 |
|---|---|---|---|
| app-version.md | `GET /api/v1/app-version?platform=` | 완료 | 200(snake_case: `platform`/`min_version`/`updated_at`, UTC Z), 400 `VALIDATION_ERROR`(누락·미지 값), 404 `NOT_FOUND`(레코드 없음). **인증 불요** |

- conventions.md 규약 선반영: base path `/api/v1`, snake_case(전역 `property-naming-strategy=SNAKE_CASE`), UTC ISO-8601, 오류 `{code,message}`, 오류코드 초안 집합(`ErrorCode` enum), 페이지 래퍼(`PageResponse`).
- crew-api.md / session-api.md 구현은 **배치 B 소관**(이번 배치 아님).

---

## 3. 미완료·보류 사항

- **[보류-1] Hibernate `ddl-auto: none` (validate 아님).** 스키마 진실은 Flyway. Instant↔`TIMESTAMP(6)`의 방언별 타입명 검증이 라이브 MySQL 대조 없이는 취약해, 무인 재기동(`restart:unless-stopped`)을 깨뜨릴 위험을 피하려 none으로 둠. **배치 B에서 실제 MySQL로 매핑 확인 후 validate 승격**. (application.yml에 근거 주석 명시)
- **[보류-2] 구조화 로그(JSON) 인코더 미배선.** 현재 콘솔 로그(UTC 타임스탬프)만. 운영 프로필에서 JSON 인코더(logback-json 등) 배선은 인프라 항목(M0)으로 이월.
- **[보류-3] 인증 필터·JWT 미구현(설계 M-6 = 배치 B).** app-version만 인증 불요로 존재. 나머지 API 인증은 배치 B.
- **[미검증] Docker Compose 런타임.** 파일은 완비(A-B2 AC 충족)했으나 `docker compose up` 실제 기동은 이 환경에서 미실행(이미지 빌드에 JDK 25 베이스 이미지 pull 필요). QA 경계면으로 이관(아래 §5).
- **범위 외/2차 미착수**: 이벤트 리스너, 도메인 애그리거트, FCM — 전부 배치 B 이후(설계 §0 미룸 표와 일치).

---

## 4. test-engineer 이관: 순수 도메인 함수 목록

- **해당 없음.** 배치 A는 스키마·계약·골조만 확정하며 도메인 로직(FinishPolicy·RankingPolicy·TrackRefinement·추월 계산)은 배치 B 이후 구현 대상이다. 이번 배치에 IO·시계·랜덤 없는 순수 함수는 없다. (app-version은 단순 조회 어댑터로 골든 테스트 대상 아님 — 슬라이스 통합 테스트로 충분히 커버.)

---

## 5. QA 검증 경계면 (점진 QA 트리거)

1. **계약↔서버 3자 대조 (app-version)**: 응답 JSON 필드명이 계약 app-version.md와 정확히 일치(`platform`, `min_version`, `updated_at`), `updated_at`이 UTC `Z` 접미 ISO-8601. 400/404 오류가 `{code,message}` shape(conventions.md §4).
2. **시간대 규약 경계**: JVM 기본 타임존 UTC, MySQL 세션 타임존 UTC(`SELECT @@session.time_zone`), Instant 왕복 저장/조회 시 오프셋 손실 없음 — 라이브 MySQL에서 확인 필요(빌드 테스트는 웹 슬라이스라 DB 미접촉).
3. **Flyway V1 적용**: `docker compose up` 시 17개 테이블 생성·마이그레이션 성공, FK ON DELETE 정책(device_token/track_payload=CASCADE, rank_entry 등=RESTRICT) 실제 반영.
4. **Docker Compose 기동**: 앱 컨테이너가 MySQL healthy 후 부팅, `/actuator/health` 200(green), `restart: unless-stopped` 적용.
5. **헥사고날 경계**: `domain` 패키지에 Spring/JPA 애노테이션 부재(현재 빈 골조라 자동 충족, 배치 B에서 재검증 대상).

---

## 6. R-003 수정 + 라이브 검증 완주 (QA 2차 차단 B2-1 대응, 2026-07-04 추가)

### 6.1 수정 내역
- **버그**: `rank`는 MySQL 8.0.2+ 예약어 — `V1__init.sql`의 rank_entry·reward_item에서 백틱 없이 사용돼 Flyway ERROR 1064 → 앱 부팅 불가(QA 라이브 재현, R-003).
- **수정**: `V1__init.sql` 2곳 백틱 인용(`` `rank` ``) + 예약어 경고 주석. **컬럼명은 계약·설계대로 `rank` 유지**(이름 변경 아님).

### 6.2 재발 방지 장치 — red→green 증명 (golden-testing 스킬 절차)
- 테스트: `src/test/java/com/runningcrew/migration/R003FlywayMigrationLiveTest.java` — Testcontainers(MySQL 8) 실 DB에 Flyway V1 전체 적용 → 성공 + **17개 테이블 전수** + `rank_entry.rank`/`reward_item.rank` 컬럼 존재 검증. `./gradlew build`에 편입(상시 실행).
- **순서 준수 증명**: ① 수정 **전** SQL로 테스트 먼저 실행 → **실패(red)**: `FlywayMigrateException → SQLSyntaxErrorException, Error Code 1064, near 'rank INT NULL...' (V1__init.sql Line 188)` — B2-1과 동일 재현. ② 백틱 수정 적용. ③ 재실행 → **통과(green)**. ④ `./gradlew build` 전체 통과(테스트 5개 = 슬라이스 4 + 마이그레이션 1).
- 근거: DB 미접촉 슬라이스 테스트는 이 유형(예약어 구문 오류)을 구조적으로 영구 미검출 — 실 MySQL 적용 테스트가 유일한 자동 방어선.

### 6.3 라이브 검증 완주 (QA 2차 미도달분)
| 단계 | 수행 | 결과 |
|---|---|---|
| `docker compose up -d mysql` | 실행 | healthy |
| 앱 기동 (`./gradlew bootRun`, compose MySQL 대상) | 실행 | **부팅 성공** — Flyway `1 init success=1` |
| 테이블 전수 (information_schema) | 실행 | **17개 전부 생성** (+flyway_schema_history) |
| FK 정책 라이브 확인 | 실행 | rank_entry(result·user)=**RESTRICT**, device_token·track_payload=**CASCADE** — 설계 §2 일치 |
| `curl /actuator/health` | 실행 | **200** `{"status":"UP"}` |
| `curl /api/v1/app-version?platform=ANDROID` (레코드 없음) | 실행 | **404** `{"code":"NOT_FOUND","message":...}` — 계약 일치 |
| seed 삽입 후 재요청 | 실행 | **200** `{"platform":"ANDROID","min_version":"1.2.0","updated_at":"2026-07-01T00:00:00Z"}` — **계약 app-version.md 예시와 문자 단위 일치**(snake_case·UTC Z). Instant 왕복(UTC 저장→Z 직렬화) 무손실 확인 |
| `platform` 누락 / `WINDOWS` | 실행 | 둘 다 **400** `{"code":"VALIDATION_ERROR",...}` — 계약 일치 |
| `docker compose down -v` | 실행 | 볼륨 포함 원복 완료 |

- 잔여 미검증: **앱 Docker 이미지 자체 기동**(bootRun으로 대체 — JDK 25 베이스 이미지 pull 비용. QA 2차와 동일한 한계, 3차 이월 유지).
- 배치 B 주의 승계: rank_entry/reward_item JPA 엔티티 매핑 시 `@Column(name = "\`rank\`")` 또는 `globally_quoted_identifiers` 필요(QA 이월 항목 5).
- 문서 갱신: `docs/regressions.md` R-003 → **CLOSED**(장치 경로 기록), 설계문서 §2.12/§2.14에 예약어 각주 추가(오케스트레이터 위임).

## 7. 팀 통신

- **domain-analyst**: 계약 모호점 없음. app-version 404 처리(레코드 없음 → 404 NOT_FOUND)를 계약 "제안"대로 구현·라이브 확인. 설계문서 §2.12/§2.14에 R-003 예약어 각주를 오케스트레이터 위임으로 추가했음 — 이견 시 통지 바람.
- **flutter-dev**: `GET /api/v1/app-version` 계약대로 구현 완료(소비 가능). 응답 snake_case 확정 — **라이브 실응답이 계약 예시와 문자 단위 일치 확인됨**.
- **qa**: §5 경계면 중 2(시간대)·3(Flyway 17테이블+FK)·4(health) **라이브 검증 완료**(§6.3). 남은 이월: 앱 Docker 이미지 자체 기동, 배치 B JPA `rank` 인용 확인.
