# 64 — Flutter 구현 보고 (M2-C: 결과 표면 — 결과·대기·히스토리·PB·승격·503 UX)

> 작성: flutter-dev · 2026-07-05 · 기준: `61_planner_plan_M2C.md`(C1·C2·C5), `62_analyst_design_M2C.md`(§4 화면 데이터 요구), 개정 계약 `history-api.md` v0.1·`track-api.md` v0.1.2·`auth-api.md` v0.1.1·`course-api.md` v0.2(§4 promote)
> 완료 기준: `flutter analyze` 0 · `flutter test` **202개 전체 통과**(신규 +33) · `flutter build apk --debug` **성공**(목킹 — 서버 불요)
> 범위: M2-B0 track DTO·리포지토리 위에 화면. **화면 작업만 + 승격 배선**. 진행 화면·리커버리·실배선은 게이트 뒤(M2-B 본체 — 이번 아님).

> **수정 이력**
> - 2026-07-05 (R-009 해소, QA 7차): **C2 대기 화면 오도성 n/m 카운트 제거**. 서버가 participation 을 세션 확정 시 일괄 전이(all-or-nothing)하므로 대기 구간엔 FINISHED/DNF 가 증명적으로 항상 0 → "0/3명" 오도. `WaitingProgress`(파생 카운트) **삭제**, 정성 문구("다른 참가자들을 기다리는 중… 전원 완료 또는 마감 시각에 순위가 확정") + **업로드 마감 시각** + **실시간 STARTED("지금 뛰는 중")** 로 대체. `result_screen_test.dart` 대기 픽스처를 **도달 가능한 상태**(전원 STARTED)로 정정(종전 FINISHED2+STARTED1 은 대기 구간 도달 불가 — 결함 은폐). 아래 §1 C2·§3 5번은 이 개정으로 대체됨. analyze 0 · test **204** 통과. qa 인계 원칙: **"계약에 없는 파생값을 UI 에 만들지 않는다."**

---

## 1. 구현 (화면 + 배선)

### C1 세션 결과 화면 — `lib/features/race/result_screen.dart`
- `GET /sessions/{id}/result`(track-api §3, M2-B0 `sessionResultProvider`) 소비. 다크 서피스(1a 라임 "결과·순위·보상" 섹션 준거), 기록 숫자 **Space Grotesk**(`AppTypography.record`).
- **동률 공동순위**: 서버 산정 `rank` 그대로 렌더(1,1,3 — 위젯 테스트가 rank '1' 2개 확인, 1·2 아님).
- **PB 뱃지**: `is_pb == true` 만 "🏅 개인 최고 기록"(테스트: 정확히 1개).
- **DNF/DNS 하단**: 서버 정렬 순서(완주→DNF→DNS) 유지, rank null → "-", 상태 라벨(미완주/불참).
- **P46-1 키부재=null 안전 렌더**: `ResultFormat.time(null)`→"--:--", `pace(null)`→"-", `distance(null)`→"-". DNS 항목(record/pace/distance 키 부재)도 크래시 없이 렌더(테스트로 박제).
- **리플레이 진입 자리는 M3 placeholder**(반투명 비활성 "다음 업데이트 · M3").

### C2 결과 대기 화면 — `result_screen.dart` `_WaitingView`
- `ResultQueryOutcome.ResultPending`(409 RESULT_NOT_READY) → "업로드 완료 — 전원 완료 대기 중 (n/m명)".
- **n/m은 참가자 상태로 계산**(`WaitingProgress.fromParticipants`): total(m)=STARTED+FINISHED+DNF(실주행자), done(n)=FINISHED+DNF(최종화). `sessionDetailProvider` 재사용. 당겨서 새로고침·재확인으로 Ready 전환.
- 세션 상세에서 자연 진입: `session_detail_screen.dart` 에 FINALIZING/COMPLETED 시 "결과 확인/결과·순위 보기" 버튼 → `/sessions/:id/result`.
- **주의(경계 명시)**: 진행 화면(경과·거리·라이브 지도)과 **별개** — C2는 서버 상태만 소비(기기 무관).

### C5 히스토리·PB 화면 — `lib/features/history/history_screen.dart`
- 탭 2개: **기록**(`GET /me/records`)·**개인 최고**(`GET /me/personal-bests`) — `history_providers.dart`.
- 기록 카드: 코스명·일시·기록·페이스·거리·순위, **"취소된 세션" 배지**(`session_cancelled`), DNF="미완주" 표기, PB="🏅 PB"(완주만).
- **승격 버튼**: `RecordHistoryItem.canPromote`(FINISHED만) 항목에만 "코스로 만들기" 노출. DNF·(비완주) 미노출.
- **승격 배선**: 이름 입력 → 세션 상세로 `crewId` 해석 → `courseRepository.promote(crewId, sourceTrackRecordId, name)`(course-api §4) → 성공 시 `crewCoursesProvider(crewId)` invalidate(**코스 목록 반영**). 오류는 **code 분기**(COURSE_PROMOTION_INELIGIBLE/FORBIDDEN/CREW_CLOSED/NOT_FOUND — 메시지 매칭 금지).
- 진입: 설정 화면 "내 기록 · 개인 최고" 타일 → `/history`.

### 401 vs 503 UX 분기 — `lib/features/auth/login_screen.dart`
- `AUTH_KAKAO_UNAVAILABLE`(503) → "카카오 로그인이 일시적으로 불안정해요. 잠시 후 다시 시도" — **재로그인 유도 없음**(로그인 화면 유지, 루프 금지). `AuthErrorCodes.kakaoUnavailable` 상수 추가.
- 401 `kakaoTokenInvalid` → "카카오 인증에 실패"(자격 문제, 다른 문구). 위젯 테스트로 두 경로 분리 검증. 503은 401 인터셉터(refresh/expire) 미개입이라 구조적으로도 루프 불가.

### DTO·enum 재사용 + 신규 (item 5)
- `lib/core/model/history_dtos.dart`: `RecordHistoryItem`·`PersonalBestItem`. `finish_status` enum 은 **track_dtos `FinishStatus`({FINISHED,DNF}) 재사용**(신규 값집합 도입 없음 — 히스토리엔 DNS 부재). **키부재=null(P46-1)**, **avg_pace_s_per_km 필드명 고정(P46-2)**, `session_cancelled` 파싱. 계약 대조 테스트로 값집합·DNS 부재 박제.
- 리포지토리: `lib/data/history_repository.dart`(신규), `course_repository.dart` `promote` 추가, providers 에 `trackRepositoryProvider`·`historyRepositoryProvider` 배선.

---

## 2. 테스트 (신규 +33, 전체 202 통과 · 목킹)

| 파일 | 검증 |
|---|---|
| `test/core/model/history_dtos_test.dart` | finish_status 재사용 값집합·DNS 부재·P46-1 키부재=null·avg_pace 필드명·canPromote(FINISHED)·CANCELLED 배지 |
| `test/data/history_repository_test.dart` | me/records·me/personal-bests 경로·파싱, **promote 201/409 INELIGIBLE/403 매핑** |
| `test/features/race/result_screen_test.dart` | 동률 공동순위(rank 1×2)·PB 뱃지 1개·DNF/DNS 하단·**--:-- null 안전**·pending n/m(2/3) |
| `test/features/history/history_screen_test.dart` | 취소 배지·DNF 표기·PB 뱃지·승격 버튼 FINISHED만·승격 왕복(trackId 42·crewId 12)·PB 탭·빈 상태 |
| `test/features/auth/login_kakao_unavailable_test.dart` | 503 재시도 안내(루프 없음) vs 401 인증 실패 분리 |

빌드: `build/app/outputs/flutter-apk/app-debug.apk` 생성 성공(기기 없이 assembleDebug).

---

## 3. qa 경계면 (계약 ↔ 서버 ↔ 클라 3자 대조 대상)

1. **history-api §1/§2 필드**: 서버 실 응답 ↔ 클라 `RecordHistoryItem`·`PersonalBestItem` 파싱. 특히 `avg_pace_s_per_km` 필드명(P46-2)·`session_cancelled`·**DNF/CANCELLED 시 rank/record/pace 키 생략**(P46-1) 실측 대조. **C8 공유 픽스처 CI 대상**.
2. **track-api §3 결과**: 동률 공동순위(1,1,3)·정렬(완주→DNF→DNS)·DNS null 필드 — 서버 산정 ↔ 클라 렌더. CANCELLED 세션 result → **404**(track-api v0.1.2) 클라 에러 처리 대조.
3. **course-api §4 promote**: 서버 자격 게이트(본인·FINISHED·1km) 코드(`COURSE_PROMOTION_INELIGIBLE`/403) ↔ 클라 code 분기. distance 서버 재확정(CO-B3) 응답 shape.
4. **auth-api §1 503**: 서버 `AUTH_KAKAO_UNAVAILABLE`(503) ↔ 클라 재시도 UX(401 재로그인과 배타). **실 카카오 어댑터 배선 시**(현재 dev 스텁은 503 미발생) 재검증.
5. **C2 n/m 대리값 한계(경계 통지)**: 참가자 계약(`ParticipantView`)에 **per-user 업로드 플래그 부재** → "업로드 수"의 정확한 대리는 최종화 상태(FINISHED/DNF)다. 클라는 확정 진행률을 표시. 정확한 업로드 카운트가 필요하면 세션 상세/결과 계약에 필드 추가 필요(제품 결정 — QA·domain-analyst 판단 요청).

---

## 4. 보류 (게이트 뒤 / 범위 밖 / 발급 대기)

- **게이트 뒤(M2-B 본체, 스파이크 PASS 필요)**: 레이스 진행 화면·AndroidForegroundTracker 실배선·리커버리 실배선·업로드 실패 재시도 UI·온보딩(권한/배터리)·client_meta 실값. **이번 미포함**(진행 화면과 C2 결과 대기 혼동 금지).
- **M3**: 리플레이 뷰어(결과 화면 "리플레이 다시보기"는 placeholder)·구간 페이스·추월. replay-api 대기.
- **폰트 자산 보류(기존)**: Pretendard·Space Grotesk 폰트 파일 미확보 → 시스템 폰트 대체 중(`AppTypography` 단일화 지점 유지). 기록 숫자 tabular figures 로 정렬만 확보. 자산 확보 시 fontFamily 지정만으로 전 화면 반영.
- **지도 타일**: 결과·코스 미리보기 placeholder 지도(네이버/카카오 지도 SDK 키 대기 — 기존 보류).
- **서버측 M2-C(다른 에이전트)**: C4 히스토리·PB 조회 API, C6 승격 서버 처리, C7 CANCELLED 보존 — 본 화면은 계약(history-api v0.1·course-api §4) 기준으로 소비/배선. 서버 구현 완료 후 실응답 3자 대조(§3) 필요.
- **C8 공유 픽스처 CI**: result/track-me/history 실응답 바이트 픽스처 박제(P26-2 종결) — test-engineer/QA 협업 항목(본 배치는 목킹 테스트까지).

---

## 5. 계약 준수 자기검증

C1 동률 공동순위 서버값 렌더 ✅ / PB is_pb만 ✅ / DNF·DNS 하단 ✅ / **P46-1 키부재=null 안전 렌더**(--:--·-) ✅ / avg_pace_s_per_km 필드명(P46-2) ✅ / C2 n/m 참가자 상태 계산 ✅ / 세션 상세 자연 진입 ✅ / C5 취소 배지·DNF 표기 ✅ / 승격 FINISHED만·course-api §4 호출·코스 목록 반영 ✅ / enum 재사용(FinishStatus)+계약 대조 ✅ / 503 재시도 UX·재로그인 루프 금지 ✅ / 화면만(진행 화면 게이트 뒤) ✅ / analyze 0·test 202·apk debug 빌드 성공 ✅.
