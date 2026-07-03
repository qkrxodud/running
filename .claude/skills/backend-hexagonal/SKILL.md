---
name: backend-hexagonal
description: 러닝크루 앱 백엔드(Spring Boot 3.x + Java 17) 구현 컨벤션 — 헥사고날 패키지 구조, 컨텍스트별 분리, 이벤트 처리(AFTER_COMMIT), JPA 매핑 규칙, REST 컨벤션, Docker Compose 인프라. backend/ 디렉토리의 서버 코드 작성·수정·리뷰 시 반드시 읽을 것. API 구현, 도메인 서비스, 영속성, 리스너 작업이 대상.
---

# 백엔드 컨벤션 (Spring Boot 3.x + Java 17, 헥사고날)

도메인 규칙은 `domain-model` 스킬이 진실이다. 이 스킬은 그것을 **코드로 옮기는 방법**을 다룬다.

## 패키지 구조

모놀리스 + 컨텍스트별 패키지 분리. 각 컨텍스트는 헥사고날 3계층:

```
com.runningcrew
├── user | crew | race | tracking | ranking | reward   # 6개 컨텍스트, 동일 구조
│   ├── domain          # 애그리거트, VO, 도메인 서비스, 도메인 이벤트 — 프레임워크 의존 금지
│   ├── application     # 유스케이스(포트 인터페이스 + 서비스), 트랜잭션 경계
│   └── adapter
│       ├── in/web      # REST 컨트롤러, 요청/응답 DTO
│       ├── out/persistence  # JPA 엔티티, 리포지토리 구현
│       └── out/...     # fcm, kakao 등 외부 어댑터
├── replay              # 프로젝션 계층 — 애그리거트 없음. ReplayGenerationService + SnapshotRepository
└── common              # 공용 VO(좌표, 폴리라인), 이벤트 발행 지원
```

**규칙과 이유:**
- `domain` 패키지에 Spring/JPA 애노테이션 금지. 도메인 객체와 JPA 엔티티를 분리하고 어댑터에서 매핑한다 — 순수 함수 골든 테스트가 컨테이너 없이 돌아야 하고, 저장 구조 변경이 도메인에 새면 안 되기 때문.
- 컨텍스트 간 참조는 ID(Long/UUID)와 도메인 이벤트로만. 타 컨텍스트의 리포지토리 주입이 보이면 잘못된 설계다.
- 이벤트 이름은 과거형 사실: `TrackUploaded`, `RaceCompleted`, `ResultFinalized`, `CrewMemberJoined`, `UserWithdrawn`, `RewardGranted`.

## 이벤트 처리

- 발행: 애플리케이션 서비스에서 `ApplicationEventPublisher` (MVP는 스프링 인프라로 충분, 메시지 브로커 금지 — 과잉).
- 동기 후속 처리(같은 트랜잭션): `@EventListener` — 예: TrackUploaded → 정제·판정·전원 완료 체크.
- **리플레이 생성은 반드시 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`** — 순위 확정 커밋이 스냅샷 계산에 인질 잡히지 않게. 리스너 안에서 새 트랜잭션(`REQUIRES_NEW`)으로 GENERATING 저장 → 계산 → READY/FAILED 갱신.
- FCM 발송 전 `race_session.replay_notified_at` 확인·기록으로 멱등 보장. 이 갱신과 발송 판단은 같은 트랜잭션에서.

## JPA 매핑 핵심 규칙

- `track_record` ↔ `track_payload`는 **별도 엔티티 + 별도 리포지토리**. `@OneToOne(fetch = LAZY)`로 잇지 말고 아예 연관을 두지 않는다 — OneToOne LAZY는 프록시 한계로 EAGER로 새기 쉽고, 실수 한 번이면 순위 조회에 수백 KB 블롭이 딸려온다. payload 접근은 `TrackPayloadRepository`를 리플레이 생성·재정제 서비스에만 주입해 한정한다.
- 블롭·스냅샷 payload는 `@Lob` / `LONGTEXT`(JSON) 또는 `LONGBLOB`(인코딩 폴리라인). 스냅샷에는 `schema_version` 컬럼 필수.
- enum은 `@Enumerated(EnumType.STRING)` 고정 — ORDINAL은 순서 변경 시 데이터가 조용히 깨진다.
- 시각은 UTC `Instant` + DB `TIMESTAMP`. 표시 타임존은 클라이언트 소관.

## REST 컨벤션

- 경로: `/api/v1/{자원}` 복수형 — `/api/v1/crews/{crewId}/sessions`.
- 요청/응답 DTO는 `record`로 어댑터 계층에 정의. 도메인 객체를 직접 직렬화 금지 — 계약과 도메인의 독립 진화를 위해.
- JSON 필드는 **snake_case** (계약 문서 기준과 일치시킬 것 — QA의 3자 대조 대상이다).
- 오류 응답 통일: `{ "code": "COURSE_IMMUTABLE", "message": "..." }` + 적절한 HTTP 상태. 도메인 규칙 위반은 400/409, 인증 401, 권한(크루장 전용 등) 403.
- `GET /app-version` (app_min_version) 은 인증 불요 — 강제 업데이트 판단용으로 초기부터 존재해야 한다.

## 인증

카카오 로그인 단일. 카카오 회원번호(kakao_id)는 **인증 어댑터 밖으로 노출 금지** — User 컨텍스트의 KakaoAccount VO에 봉인. API 인증은 자체 발급 토큰(JWT 등)으로, 컨트롤러는 내부 userId만 다룬다.

## 인프라·운영

- `backend/docker-compose.yml`: Spring Boot + MySQL, **`restart: unless-stopped`** (정전 복구 무인 재기동 — 홈서버 전제). cloudflared는 호스트 서비스로 등록.
- MySQL 일일 덤프 스크립트 + 외부 보관은 M0 항목 — 인프라 작업 시 잊지 말 것.
- 구조화 로그(JSON), 헬스체크 엔드포인트(`/actuator/health`) 노출.

## 테스트 분업

- 순수 도메인 함수(FinishPolicy 등 4종)의 골든 테스트: **test-engineer 소관** — 함수 시그니처+예시 입출력을 넘긴다.
- 어댑터 통합 테스트(컨트롤러 슬라이스, JPA 매핑, 이벤트 리스너 연결): backend-dev가 최소한으로 직접 작성.
- 완료 기준: `./gradlew build` 통과. 실패 상태로 완료 보고 금지.
