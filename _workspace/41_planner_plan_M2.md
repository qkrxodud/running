# M2 계획 — 트래킹 완성 (계획서 §8 "리스크 최대 구간")

> 입력: 오케스트레이터 M2 배치 분할 요청 / 진실공급원 `app/docs/러닝크루_앱_계획서.md` §3·§8 / `app/docs/todolist.md` M2 섹션 / 이월(B2 QA 26·analyst 22·R-003·R-005)
> 산출 시점: 2026-07-04 (배치 A·B1·B2·3환경분리 완료, 커밋 a79fe35 기준)

## 0. 범위 판정 — 전부 MVP(M2)

todolist M2 섹션 전 항목은 계획서 §8 M2 정의("`AndroidForegroundTracker` 구현 + GPS 정제/완주 판정 골든 + 업로드 파이프라인 + 순위 확정")에 직접 대응 → **MVP 확정**. 범위 외(안티치트·공개모집·참가비) 유입 없음. 발급물 게이트(카카오/네이버/Firebase/도메인)는 M2 무관 — 지도 SDK는 리플레이가 아닌 M2엔 "코스 승격②"만 접점(M2-C로 격리), FCM은 M3.

## 1. 현재 코드 상태 대조 (재계획 방지)

| 영역 | 이미 존재 | M2에서 신규 |
|---|---|---|
| Race 서버 | Course·RaceSession·Participation·상태머신(DRAFT/OPEN/RUNNING)·컨트롤러·시더 | FINALIZING/COMPLETED 전이·마감 스케줄러·결과 확정 |
| Tracking 서버 | **탈퇴 삭제 경로만**(`TrackDataEraser`·`TrackPayloadEraseAdapter`·`TrackDataCleanupListener`) | TrackRecord 애그리거트·payload 분리 저장·업로드·정제·세그먼트 전부 |
| Ranking 서버 | **빈 패키지**(package-info만) | RankingPolicy·RaceResult·RankEntry·PB 전부 |
| 클라 코어 | 순수: track_point·track_buffer·sampling_policy·track_store·upload_queue·backoff_policy·polyline_codec·lat_lng | 업로드 DTO·클라 상태머신·리커버리/멱등 로직 |
| 클라 플랫폼 | android_foreground_tracker(스파이크 수준)·notification·permission | 실배선·적응형 샘플링 라이브·레이스 진행 화면·온보딩 |

**불일치 없음** — 계획서 대비 초과 구현 발견 없음. 탈퇴 삭제 경로가 TrackRecord보다 먼저 존재하나 이는 User 탈퇴(B1) 산물로 정합(payload 테이블 전제만 선행 정의).

## 2. 배치 구조 (3분할)

```
M2-A  서버 심장부 (순수함수+영속+업로드+마감) ── 스파이크 게이트 無관, 즉시 착수
M2-B  클라 트래킹 실배선 (트래커→저장→업로드·진행화면·엣지·온보딩) ── 실기기 스파이크 PASS 관문 뒤
        └ B0(비기기: 상태머신·업로드DTO·큐배선)은 A1 계약 동결 후 병렬 가능
M2-C  결과 표면 (순위/기록/PB 화면·코스승격②·공유픽스처CI·다기종 실주행)
```

분할 기준: **실기기 의존성**과 **리스크(불확실성) 우선순위**. 계획서 §8 원칙(트래킹 스파이크가 성립 조건 → fail fast)에 따라, 검증 없이 진행 가능한 서버 순수 로직(A)을 전면 배치하고, 실기기·실배선(B)을 게이트 뒤로 미룬다.

---

## 3. 1차 배치 M2-A — 상세 (11개 작업)

형식: `[영역] 작업명 — 수용 기준 — 의존 — 이월 흡수`

### 계약
- **A1** `[domain]` track-api 계약 정의 — 업로드 엔드포인트 + `TrackUploadRequest`(인코딩 폴리라인 + timestamp/speed/altitude/accuracy 병렬배열 + `client_meta{os,os_version,device_model}`) + `TrackUploadResponse` + 결과/순위 조회 응답 스키마 — **수용**: `docs/contracts/track-api.md` 작성, enum 값집합·필드·에러코드·GPS시각 필드 명시, 계약 vs 서버 vs 클라 3자 대조 가능 — **의존**: 없음(선행 관문) — **흡수**: `client_meta` 플랫폼종속 필드를 스키마 밖 부가필드로 격리(todolist 명시).

### 영속 심장부
- **A2** `[backend]` TrackRecord 애그리거트 + `track_record`(메타·요약)/`track_payload`(raw+refined 블롭) **1:1 분리** — **수용**: 일반 조회 경로는 track_record만 참조, payload는 리플레이생성·재정제 전용 리포지토리로만 접근(별도 포트), 조회 어댑터에 payload 조인 0건 — **의존**: A1 — **흡수**: track_payload 격리(QA §4 — **순위·결과 조회 경로 등장하므로 A7/A8 완료 후 QA 재검증 트리거**), 원시+정제 이중 보존.
- **A3** `[backend]` TrackUploadService — 수신·폴리라인 디코딩·**시간 정합성 검증만** — **수용**: 역순/미래 타임스탬프 거부, 동일 participation 재업로드 정책 적용(O-M2-4 결정 반영), **코스 이탈 검증 중복 구현 0건**(FinishPolicy로 일원화) — **의존**: A1·A2.

### 순수 함수 (골든 — 이 마일스톤의 핵심 산출물)
- **A4** `[domain][test]` TrackRefinementService(순수) — accuracy 임계 제거·이동평균 스무딩·이상점프 보정·**정제 후 좌표로 거리계산**(원시 하버사인 누적 금지)·원시+정제 동시 보존·GPS공백 구간 식별(리플레이 보간 메타) — **수용**: 골든 테스트(실주행 픽스처 최소 1개 축적), 임계값(accuracy 등) 외부화 — **의존**: A1 — **흡수**: **그로스 타임**(자동 일시정지 없음 → 정제는 정지구간을 삭제/보정하지 않음, 신호대기 포함 실경과), 기록유실 대응(공백 메타).
- **A5** `[domain][test]` TrackSegment 구간 페이스 요약(순수) — **수용**: 구간 경계(500m 잠정, O-M2-3) **외부화**, 골든 — **의존**: A4.
- **A6** `[domain][test]` FinishPolicy(순수) — ①도착 반경30m ②정제거리≥코스90% ③코스일치율(정제포인트 80%가 폴리라인 50m내), 3조건 AND=완주/미충족=DNF(기록·경로 보존) — **수용**: 골든(지름길 주파/다른길 오진입/정상완주), **임계값 30m·90%·80%·50m 전부 설정값 외부화**, 코스이탈 검증 여기로 일원화 — **의존**: A4.
- **A7** `[domain][test]` RankingPolicy(순수) — 완주 오름차순·**동률 공동순위+다음 건너뜀(1,1,3)**·DNF/DNS 순위미부여 하단표기·PB(유저×course_id) — **수용**: 골든(동률·DNF/DNS 혼합) — **의존**: A6.

### 결과·마감 전이
- **A8** `[backend]` RaceResult/RankEntry 영속 + `ResultFinalized` 이벤트 — **수용**: RankEntry(순위·기록·평균페이스·PB여부) 저장, **`rank` 컬럼 예약어 인용**(백틱 또는 globally_quoted_identifiers) — **의존**: A7 — **흡수**: **R-003 이월5**(rank_entry 매핑 시 JPA 인용, red→green 회귀 재발방지 연결).
- **A9** `[backend][domain]` SessionClosePolicy(순수) + 마감 스케줄러 — RaceCompleted 트리거=전원 업로드 OR upload_deadline 도달, STARTED후 미업로드→**DNF**, 미출주 REGISTERED→**DNS**, **OPEN·RUNNING 모두 FINALIZING 진입 허용**(STARTED 신호 유실 내성) — **수용**: clock 주입 테스트로 deadline 전이 재현, 스케줄러 idempotent — **의존**: A7·A8 — **흡수**: **미규정-2**(STARTED 유실 시 OPEN→FINALIZING 직행)·**미규정-4**(FINALIZING 취소 정책 O-M2-2 결정 반영).
- **A10** `[backend]` TrackUploaded → Race 통지(AFTER_COMMIT 이벤트) — **수용**: 업로드 커밋 후 전원 업로드 여부 재평가 → 조건 충족 시 FINALIZING 전이 — **의존**: A3·A9.

### 정합성 장치
- **A11** `[test][qa]` 서버응답↔클라DTO 공유 픽스처 CI — **수용**: 서버 upload/result 실 응답 JSON을 픽스처로 박제 → 앱 테스트가 동일 픽스처 파싱(교차 CI화) — **의존**: A1·A8 — **흡수**: **P26-2/P16-1**(race DTO 교차 CI 부재) 연장 종결.

**작업 수·분포(1차 배치)**: 총 11개 — domain 순수/골든 4(A4·A5·A6·A7, test-engineer 페어) · backend 영속·서비스·마감 5(A2·A3·A8·A9·A10) · 계약 1(A1) · 정합성CI 1(A11). 실기기 의존 **0** → 스파이크 게이트 무관, 즉시 병렬 착수 가능.

---

## 4. 2차·3차 배치 (개요 — 상세화는 M2-A 완료 후)

### M2-B 클라 트래킹 실배선 (실기기 스파이크 PASS 관문 뒤)
- `[flutter]` AndroidForegroundTracker 실구현: Foreground Service 상시기록 알림 + **적응형 샘플링 라이브**(주행 3~5초/정지 10초 완화/재개 복귀) + **GPS 시각 우선** 타임스탬프
- `[flutter]` 트래커→버퍼→로컬저장→업로드 큐 전 배선(레이스 중 서버 없이 완결, 완주 후 사후 업로드) + 업로드 실패율 계측(구조화 로그, M4 확인루틴 데이터소스)
- `[flutter]` 클라 로컬 상태머신 READY→RUNNING→FINISHED_LOCAL→UPLOADED **[B0: 순수, A1 동결 후 병렬 가능]** — RaceStatus↔로컬상태 대응(analyst 이월)
- `[flutter]` 레이스 진행 화면(경과·거리·페이스·지도·종료) + 사전점검(권한·위치서비스·GPS수신) + 결과 대기 화면(n/m명)
- `[flutter]` 엣지: 강제종료 복구(이어가기/그시점 종료)·중복시작 방지(멱등/거부)·업로드 실패 수동 재시도 버튼·서버다운 오프라인 UX
- `[flutter]` 온보딩 UX: 배터리 최적화 예외 안내·권한 요청 플로우(PermissionService)
- `[test]` 갤럭시 실기기: 절전·앱 자동종료·화면 꺼짐 장시간

### M2-C 결과 표면 (M2-A 결과 + M2-B 뒤)
- `[flutter]` 순위표·기록 히스토리·PB 화면(세션별 순위·개인 누적·PB)
- `[flutter+backend]` 코스 등록② 과거 주행기록 코스 승격(TrackRecord 선행 필요 — M1에서 이동)
- `[test]` 실기기 **다기종**(갤럭시 외) GPS 편차 확인
- 실주행 원시 트랙 픽스처 지속 축적(A4 회귀선 확장)

---

## 5. 마일스톤 매핑

전 작업 **M2**. A9 마감 스케줄러·A10 이벤트는 §8 M2 "순위 확정" 전제. M2-C 코스승격②는 계획서상 M1→M2 이동 명시 항목. M2-B 실기기 테스트가 §8 "리스크 최대 — 실기기 테스트 넉넉히" 대응.

## 6. 스파이크 게이트 처리 방식

**관문**: M1 실기기 1시간 백그라운드 기록 검증 미수행(사용자 대기) = 계획서 §8 fail-fast 성립조건.

- **M2-A 전체**: 서버 순수 로직·계약·영속으로 실기기 무관 → **게이트 무시하고 즉시 착수**. 스파이크 결과와 독립적으로 가치 보존.
- **M2-B**: 실기기 스파이크 **PASS 전 착수 금지**. 단 device-무관 하위(B0: 클라 상태머신 순수·업로드 DTO 직렬화(A1 계약 소비)·업로드 큐 배선)는 A1 동결 후 병렬 허용 → B를 B0/B-device로 절단.
- **스파이크 FAIL 시**: AndroidForegroundTracker 실구현 태스크를 `flutter_background_geolocation`(유료) 전환 태스크로 **치환**(계획서 §8 fail fast). M2-A는 영향 없음 — 업로드 계약이 트래커 구현을 추상화하므로 payload 형식만 유지되면 서버 심장부 재작업 0.

## 7. 열린 질문 (사용자/제품 결정 필요 — 오케스트레이터 에스컬레이션)

- **O-M2-1 (W26-1 정본)**: '레이스 시작' 버튼 — B2에선 "disabled stub vs 실 STARTED 신호" 갈림. M2에서 실주행 트래킹이 배선되므로 이 버튼은 **실 트래커 기동으로 승격**(스파이크 PASS 후 활성)이 자연 귀결. **제안**: disabled stub 폐기 확정. 사용자 확인 요망.
- **O-M2-2 (미규정-4)**: FINALIZING 중 cancel 최종 정책 — 산정 결과 폐기 동반 취소 허용 vs 마감 진입 후 취소 불가. 계획서 미규정 → 제품 결정.
- **O-M2-3 (초기 운영 임계값)**: FinishPolicy 30m/90%/80%/50m·TrackSegment 500m·정제 accuracy 임계의 **초기 디폴트값** 승인 주체. 계획서는 "튜닝 대상"만 명시, 외부화는 확정이나 첫 값 필요 → 도메인 분석가 제안 후 사용자 승인.
- **O-M2-4 (재업로드 정책)**: 마감 전 동일 participation 재업로드 시 — 최초만 채택 vs 더 나은 기록으로 갱신 vs 거부. 계획서 미규정 → **A3 전제**이므로 우선 결정 필요.
- **O-M2-5 (PB 후보 범위)**: DNF 기록도 PB 후보인가 — **제안**: 완주 기록만(기본), 사소하나 A7 확정 필요.

## 8. 계획서 대비 누락 점검 (자기검증)

임계값 외부화(A6·A5·A4) ✅ / 원시+정제 이중보존(A2·A4) ✅ / 그로스타임 자동일시정지 없음(A4 흡수) ✅ / 정제후 거리계산·원시하버사인 금지(A4) ✅ / GPS 시각 우선(A1 필드·M2-B) ✅ / track_payload 격리(A2 + QA 재검증) ✅ / 동률 공동순위 건너뜀(A7) ✅ / DNF/DNS 마감정리(A9) ✅ / client_meta 스키마 격리(A1) ✅.
