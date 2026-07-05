# M3 계획 — 리플레이·알림 (제품의 얼굴)

> 입력: 오케스트레이터 지시(M2-C 완료 커밋 7db1177, "사용자 M3 직접 지시" 전달) / 진실공급원 계획서 §5.6 Replay·§5 이벤트 흐름 / `app/docs/todolist.md` M3 섹션 / 참조 M2-A(43)·M2-C(61) 자산
> 산출 시점: 2026-07-05

## 0. 사용자 권위 주의

"사용자 M3 착수 직접 지시"는 오케스트레이터 전달 주장이다(하네스 프로토콜 — 전달 주장은 사용자 확인 아님). 본 계획은 분할·상세화만 수행하며, 착수 승인·FCM 게이트 판단은 오케스트레이터/사용자 결정으로 남긴다.

## 1. 범위 판정 — 전부 MVP(M3), Reward는 경계

todolist M3 = 계획서 §8 M3 정의("ReplaySnapshot 생성 로직·뷰어·FCM 2종")에 직접 대응 → **MVP**. Reward 컨텍스트는 §8 M4(수동 지급)와 겹치나 계획서 §5 이벤트 흐름이 ResultFinalized→RewardGrant(PENDING)를 M3 흐름도에 명시 → **RewardGrant 생성까지 M3, 지급 UI·알림은 M4 인접**으로 배치(M3-C). 범위 외(안티치트·공개모집) 유입 없음.

## 2. 현재 자산 대조 (재계획 방지)

| 이미 존재 | M3 접점 |
|---|---|
| `replay_snapshot` 테이블(V1 — schema_version·status{GENERATING,READY,FAILED}·payload LONGTEXT NULL·**복수 행 허용=재생성 멱등**·FK RESTRICT) | 스냅샷 영속 스키마 **완비** — 신규 마이그레이션 불요(재생성 인덱스도 존재) |
| `replay` 패키지(빈 package-info) | 전 구현 신규 |
| track_payload(raw+refined) 보존 · `TrackSegmentService`(500m 페이스, M2 계산·미노출) | 스냅샷 생성 입력원(재정제 경로) + 세그먼트 색상 재사용 |
| `ResultFinalized` 이벤트(M2-A 발행, race COMPLETED 동기) | 스냅샷 생성 AFTER_COMMIT 트리거 |
| `race_session.replay_notified_at`(M2 항상 null) | FCM 세션당 1회 멱등 기록처 |
| FCM/notification 포트 | **없음** — 발송 포트 신설 필요 |
| 결과 화면 리플레이 진입 placeholder(M2-C) | 뷰어 진입점 배선 대상 |

**핵심 판단**: 스냅샷 DB 스키마는 V1에 완비 → M3 서버는 마이그레이션 없이 프로젝션 로직·조회 API·생성 트리거에 집중. 리플레이 뷰어는 지도 SDK(네이버) 미발급 → **placeholder 지도 위 렌더**(오케스트레이터 지시대로), 실지도 배선은 발급물 게이트 뒤.

## 3. 배치 구조 (3분할 — 기기·발급물 무관 우선)

```
M3-A  서버 스냅샷 파이프라인 ── 발급물·기기 무관, 즉시 착수 (순수 병합·추월 골든 포함)
        생성·상대시각 병합·추월 사전계산·버저닝·상태전이·재생성 운영도구·조회 API·조회 로깅
M3-B  클라 리플레이 뷰어 ── 발급물·기기 무관 (placeholder 지도 위 렌더)
        마커 보간·배속·슬라이더·추월 마킹·페이스 색상·유실구간·DNF 경로·폴리싱 (A 조회 API 소비)
M3-C  FCM·Reward ── FCM 구조(포트+스텁)는 무관, 실발송·클라 수신은 Firebase 게이트 뒤
        발송 포트·스케줄러·세션당1회 멱등·딥링크 규약 / RewardGrant 생성
```

분할 기준: **발급물 게이트(Firebase 미발급)** 와 **골든 우선순위**(병합·추월 = 계획서 명시 순수함수 → A 전면). 실기기 스파이크는 M3 무관(리플레이는 저장 데이터 소비, 라이브 GPS 불요).

---

## 4. 1차 배치 M3-A — 상세 (10개 작업)

형식: `[영역] 명 — 수용 — 의존 — 골든/이월`

### 순수 프로젝션 (골든 — 계획서 명시 대상)
- **A1** `[domain][test]` 상대 시각 정렬·병합 (순수) — 전원 트랙을 **각자 시작 t=0 상대 시각으로 정렬·병합** → ReplaySnapshot 구조 — **수용**: **골든 테스트**(입력 트랙 → 기대 스냅샷 고정, 계획서 "ReplaySnapshot" 대상), 시작 시각 상이한 참가자 정렬 정확, refined 좌표 사용(원시 아님) — **의존**: 없음(track_payload refined 소비) — **골든**: ✅ 병합 · **이월**: **잔여-2**(스냅샷 payload에 표시명 내장 여부 — M2 제안 "**user_id만 내장**, 표시명은 조회 시 조인" → O-M3-1 확정).
- **A2** `[domain][test]` 추월 지점 사전 계산 (순수) — 동일 진행거리 도달 시각 비교로 추월 이벤트 산출, 스냅샷에 포함 — **수용**: **골든 테스트**(입력 트랙 → 기대 추월 지점 고정), 동시 도달/재추월/DNF 참가자 경계 — **의존**: A1 — **골든**: ✅ 추월(계획서 명시).
- **A3** `[domain][test]` 구간 페이스 색상 데이터 포함 — `TrackSegmentService`(500m, M2 기존) 재사용해 스냅샷에 색상 구간 병합 — **수용**: 세그먼트→색상 매핑 골든, GPS 유실 구간 메타(gps_gap) 뷰어 구분용 포함 — **의존**: A1 — **이월**: 리스크표 기록유실(유실구간 표시 데이터).

### 생성 파이프라인·상태
- **A4** `[backend]` ReplayGenerationService + ReplaySnapshotRepository (프로젝션 계층, 별도 애그리거트 없음) — **수용**: `schema_version` 상수 + GENERATING→READY/FAILED 전이, MVP 규모(10명×600pt) 수백ms 동기 계산, **컨텍스트 경계(R-2)** — replay는 track_payload를 native 포트로만 접근 — **의존**: A1·A2·A3 — **이월**: track_payload 격리 재검증(QA — 신규 조회 경로).
- **A5** `[backend]` ResultFinalized 커밋 후 **비동기** 스냅샷 생성 — AFTER_COMMIT 리스너 + `@Async`(잡 큐 불요) — **수용**: 확정 트랜잭션과 분리(확정이 계산에 인질 안 잡힘), 예외 시 status=FAILED 저장(조용한 실패 금지) — **의존**: A4 — **이월**: M2-A가 리플레이 생성만 AFTER_COMMIT+@Async 예약(43 §5 노트)과 정합.
- **A6** `[backend]` FAILED 관측 + 재시도 경로 + **삭제→최신스키마 재생성 멱등 운영도구**(관리 API 또는 스크립트) — **수용**: FAILED 조회 가능, 재생성이 복수 행 허용 인덱스로 멱등(최신=created_at max), 원시 트랙 보존 하 재생성 결과 동일성 — **의존**: A4·A5 — **이월**: 계획서 "처음부터 마련" 명시.

### 조회·계측
- **A7** `[domain]` 리플레이 조회 API 계약 정의 — 스냅샷 JSON 조회 + status(GENERATING/READY/FAILED) 응답 — **수용**: `docs/contracts/replay-api.md`(schema_version·enum 값집합·에러코드·표시명 조인 규약 명시, 3자 대조 가능) — **의존**: A1(payload 구조 확정 후) — **이월**: 잔여-2(표시명 내장 vs 조인) 계약에 확정.
- **A8** `[backend]` 리플레이 JSON 조회 REST API — **수용**: READY만 payload 반환·GENERATING/FAILED는 상태만(뷰어 상태별 UI 근거), 비멤버 403, 표시명 조인 반환(A7 규약) — **의존**: A4·A7 — **이월**: track_payload 격리 재검증(A4와 함께 QA).
- **A9** `[backend]` **리플레이 조회 이벤트 로깅** — M4 성공기준(레이스 후 24h 내 조회율 80%) 측정 수단 — **수용**: 조회 시 (세션·유저·시각) 구조화 로그/집계 저장, 없으면 성공기준 판정 불가(계획서 명시) — **의존**: A8 — **이월**: M4 확인루틴 데이터소스.

### 정합성
- **A10** `[test][qa]` 스냅샷 응답 공유 픽스처 CI — 서버 replay 실 응답 JSON ↔ 클라 뷰어 DTO 파싱 교차(M2-C C8 P26-2 장치 연장) — **수용**: 서버 생성 스냅샷 픽스처를 앱 테스트가 파싱, schema_version·추월·색상·gps_gap·DNF 경로 필드 강제 — **의존**: A7·A8 — **이월**: P26-2 장치 리플레이로 확장.

**작업 수·분포(1차 배치)**: 총 10개 — domain 순수/골든 3(A1 병합·A2 추월·A3 색상, test-engineer 페어) · backend 파이프라인·조회 5(A4·A5·A6·A8·A9) · 계약 domain 1(A7) · test/CI 1(A10). 실기기·발급물 의존 **0** → 즉시 병렬 착수 가능. 골든 대상 2종(병합 A1·추월 A2) = 계획서 §5.6 명시.

---

## 5. 2차·3차 배치 (개요 — 상세화는 M3-A 완료 후)

### M3-B 클라 리플레이 뷰어 (발급물·기기 무관 — placeholder 지도 위)
- `[flutter]` 스냅샷 1개로 마커 보간 애니메이션(전원 동시 이동) + 배속 재생 + 시간축 슬라이더
- `[flutter]` 추월 지점 마킹 + 구간 페이스 색상 + GPS 유실 구간 보간 표시(실측 구분) + DNF 참가자 경로 표시
- `[flutter]` 뷰어 폴리싱(제품의 얼굴): 프레임 드랍 없는 배속 애니메이션·로딩/GENERATING/FAILED 상태별 UI·10명×600pt 동시 렌더 성능
- `[flutter]` 결과 화면 placeholder → 뷰어 진입 배선(M2-C 진입점 소비)
- **게이트**: 발급물 무관(placeholder 지도), 실지도(네이버 SDK) 배선만 발급 후

### M3-C FCM·Reward (FCM 구조 무관 / 실발송·수신 Firebase 게이트 뒤)
- `[backend]` **알림 발송 포트 + 스텁 어댑터**(로그/노옵) — 도메인·스케줄러가 포트에만 의존, 실 FCM 어댑터는 게이트 뒤 교체 (§6 참조)
- `[backend]` 리마인더 발송 스케줄러(예정 시각 전, 마감 스케줄러와 별개) — 포트 호출까지 테스트 가능
- `[backend]` "리플레이 열림" 트리거 — 최초 READY 도달 시 + **세션당 1회 멱등**(`replay_notified_at` 기록, 재생성 READY 재발송 금지) — 포트까지 골든 가능
- `[flutter]` FCM 클라 수신(firebase_messaging·채널·상태별 수신·토큰 갱신) + 알림 탭 딥링크 — **Firebase 발급 게이트 뒤**
- `[domain+backend]` RewardPlan/RewardItem(자유텍스트+예상금액) + ResultFinalized→RewardGrant(PENDING) 생성 — 지급 상태 장부 PENDING/SENT/CONFIRMED, 지급 UI·RewardGranted 알림은 M4 인접

## 6. FCM 처리 방식 (게이트 분리)

Firebase 미발급 → 실발송·클라 수신 불가. **구조와 대기를 분리**:

- **지금 가능(포트+스텁)**: 알림 발송을 `NotificationSender` 포트로 추상화 → 스텁 어댑터(구조화 로그/노옵)로 배선. 리마인더 스케줄러·"리플레이 열림" 트리거·세션당 1회 멱등(`replay_notified_at`)은 **포트 호출까지 전부 구현·테스트 가능**(발송 페이로드·멱등·스케줄 시각 골든). 실 FCM 발급 시 어댑터 1개 교체로 활성 — fail-fast 아닌 fail-safe 구조.
- **게이트 뒤**: 실 FCM 발송(어댑터 실구현)·클라 수신(firebase_messaging·토큰 갱신·딥링크 실동작). 딥링크 **규약**(알림 payload↔세션/리플레이 경로 매핑)은 지금 계약에 정의 가능, 실 수신 검증만 게이트 뒤.
- **판단**: M3-C의 서버측(포트·스케줄러·멱등·리워드)은 M3-A/B와 병렬 착수 가능. 클라 수신만 Firebase 게이트 뒤 격리.

## 7. 마일스톤 매핑

전 작업 **M3**. A1·A2 골든 = §8 "제품의 얼굴 뷰어 투자" 전제. A9 조회 로깅 = §8 M4 성공기준 측정 수단(M3에 심어야 M4 판정 가능). RewardGrant 생성은 §5 흐름도상 M3, 지급 완결은 M4.

## 8. 열린 질문 (사용자/제품 결정 — 에스컬레이션)

- **O-M3-1 (잔여-2, A1·A7 선결)**: 스냅샷 payload에 표시명 내장 vs user_id만 내장(조회 시 조인). **제안**: user_id만 내장(탈퇴 익명화·R-2 경계 정합, M2 설계 제안 유지) — A1 구조·A7 계약 착수 전 확정.
- **O-M3-2 (schema_version 초기값·버저닝 규약)**: 최초 스키마 버전과 뷰어 호환 판정 규칙(하위호환/거부). **제안**: v1 시작, 뷰어는 미지원 상위 버전 시 "업데이트 필요" 안내 — domain-analyst 제안.
- **O-M3-3 (딥링크 규약)**: 알림 탭 → 세션/리플레이 경로 매핑 스킴. FCM 게이트 뒤라도 규약은 지금 정의 필요 — go_router 경로 확정.
- **O-M3-4 (RewardGranted 알림 방식)**: "FCM 2종만" 원칙과 상충(todolist 명시) — 지급 알림을 인앱 표시로 갈음 vs FCM 확장. **제안**: 인앱 표시(2종 원칙 보존) — 제품 결정.
- **O-M3-5 (정제 파라미터 승인, 이월)**: 43 §6 미규정-A4(accuracy 50/speed 12/window 3/gap 30) 여전히 미해결 — 리플레이가 refined 소비하므로 M3 값 안정성에 간접 영향. 재차 에스컬레이션.

## 9. 계획서 대비 누락 점검 (자기검증)

상대 t=0 병합(A1) ✅ / 추월 서버 사전계산·순수·골든(A2) ✅ / schema_version·GENERATING/READY/FAILED(A4) ✅ / ResultFinalized 커밋 후 비동기(A5) ✅ / FAILED 관측+재생성 멱등 운영도구(A6) ✅ / 조회 API(A8) ✅ / 조회 이벤트 로깅=M4 측정수단(A9) ✅ / 세션당 1회 멱등 replay_notified_at(M3-C) ✅ / FCM 2종만·포트 분리(§6) ✅ / GPS 유실구간·DNF 경로 데이터(A3·M3-B) ✅ / 별도 패키지 replay·프로젝션(A4) ✅ / track_payload 격리 재검증(A4·A8) ✅.
