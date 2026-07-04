# 24 — Flutter 구현 보고 (배치 B2: 세션 화면 + Race DTO + 지도 추상화 + 환경분리)

> 작성: flutter-dev · 2026-07-04 · 선행: `21_planner_plan_B2.md`(C1~C4 AC), `22_analyst_design_B2.md`(§4 클라 경계), `docs/contracts/course-api.md` v0.1 · `session-api.md` v0.2
> 검증: `flutter analyze` **0 issues** · `flutter test` **112 passed** · `flutter build apk --debug --dart-define-from-file=config/dev.json` **성공**

---

## 1. 완료 기준 결과

| 게이트 | 결과 |
|---|---|
| `flutter analyze` | ✅ 0 issues |
| `flutter test` | ✅ 112 passed (신규 race 테스트 6파일 포함) |
| `flutter build apk --debug` (dev dart-define) | ✅ `app-debug.apk` 빌드 성공 |
| R-002 core 순수성 (`no_platform_imports_test`) | ✅ green — race DTO·지도 위젯 모두 `lib/core` 미오염 |

---

## 2. 구현 화면·모듈

### C1 — Race DTO·enum (`lib/core/model/race_dtos.dart`)
- **enum 전수**: `RaceStatus`(6값+unknown), `ParticipationStatus`(6값+unknown). 미구현 상태값(FINALIZING/COMPLETED/FINISHED/DNF/DNS)도 **정식 파싱**(unknown 아님) — 집합 보존. `enum_codec.parseContractEnum` 폴백 경유.
- DTO: `CourseSummary`·`CourseDetail`·`SessionSummary`·`SessionDetail`·`ParticipantView`·`CreateSessionRequest`. 폴리라인 디코딩은 `CourseDetail.decodedPath`가 **기존 `lib/core/geo/PolylineCodec` 재사용**(신규 core 의존 0 — R-002).
- 계약 대조 테스트: `test/core/model/enum_contract_test.dart`에 RaceStatus/Participation 6값 집합 대조 + 폴백 추가(R-001 상시 장치 확장). `race_dtos_test.dart`에 계약 예시 JSON 파싱 검증.

### C1 — 세션 목록 (`session_list_screen.dart`)
- 크루 상세 → "레이스 세션" 카드(`crew_detail_screen.dart`에 진입점 추가) → 목록. 상태 뱃지(모집 중/진행 중/집계 중/완료/취소됨/준비 중), 일시·참가자 수. 크루장이면 "세션 만들기" FAB(crewDetail 로 leader 판정).

### C1 — 세션 상세 (`session_detail_screen.dart`)
- 코스 미리보기(placeholder 지도), 상태 뱃지, 예정·업로드 마감·보상(M3 안내), 참가자 목록(상태별 뱃지), **"지금 뛰는 중"** = STARTED 참가자 존재 시 배지(읽기 전용).
- 액션: 크루장 → **발행(open)**/**취소(cancel)**; 멤버 → **참가 신청(register)**; 참가자 → 내 상태 칩 + **레이스 시작** 진입점(§4 보류 참조).

### C2 — 세션 생성 (`session_create_screen.dart`)
- 코스 선택 = `GET /crews/{id}/courses`(dev 시드 코스) 목록. 예정 일시 피커 + **업로드 마감 기본 +12h**(`defaultUploadDeadline`, 앱레이어 UX — 미수정 시 예정 변경에 자동 추종, 사용자 수정 시 고정). 클라 검증(`upload_deadline > scheduled_at`).

### C3 — 지도 추상화 (`features/race/map/course_polyline_map.dart`)
- `CoursePolylineMap`(추상 위젯) + `_PlaceholderCoursePolylineMap`(CustomPaint 폴리라인 스케치, 실 타일 없음). `flutter_naver_map` 의존성 **미추가**. 교체 지점(어댑터·Manifest meta-data·팩토리 분기) 4단계 주석 고정. Client ID 주입은 `AppConfig.naverMapClientId`(C4) 경유.

### C4 — dev/prod 분리 (`lib/app/app_config.dart` + `config/{dev,prod}.json`)
- `AppConfig` 단일 창구(`String.fromEnvironment` 직접 호출 금지). 키: `APP_ENV`·`API_BASE_URL`·`DEV_LOGIN`·`KAKAO_APP_KEY`·`NAVER_MAP_CLIENT_ID`(키값은 placeholder — 대기). `api_client.apiBaseUrl`가 `AppConfig` 참조로 전환. `/spike` 라우트 prod 차단(`showDevTools` 게이트). 빌드: `--dart-define-from-file=config/dev.json`(config/README.md).

---

## 3. 사용 계약

- `course-api.md` v0.1: §1 생성(POST) · §2 목록(GET, 세션 생성 코스 소스) · §3 상세(GET). 폴리라인 1e5 규약은 코덱 재사용으로 충족.
- `session-api.md` v0.2: §1 생성 · §2 목록 · §3 상세 · §4 open · §5 register · §6 start · §7 cancel. 명령 4종 모두 **body 없는 POST**(경로+토큰만) — `session_repository._command`로 통일.
- `conventions.md`: PageResponse 래퍼(§6), 오류 `{code,message}` code-기반 분기.

---

## 4. QA 경계면 (교차검증 대상 · qa에게)

1. **참가 취소(unregister) 엔드포인트 부재 우려**: 계약(session-api v0.2)에 **참가자 자기-취소 명령이 없다**(register/start만, cancel은 크루장 세션 취소). 태스크 문구의 "참가 신청/취소"는 크루장 open/cancel로 해석 — 클라는 **참가 신청만** 구현. 멤버 self-unregister가 필요하면 domain-analyst 계약 추가 요청 대상. (임의 엔드포인트 발명 안 함 — 원칙 1.)
2. **`SessionDetail.course` shape**: 계약 §3의 course 요약엔 `crew_id`/`created_by`/`created_at` 부재 → DTO에서 nullable 처리. 서버가 §3 course에 이 필드를 넣는지 3자 대조 필요(현재 클라는 없어도 안전).
3. **enum 3자 대조**: RaceStatus 6값·Participation 6값 — 서버 enum ↔ 계약 ↔ 클라 DTO(`enum_contract_test`). 서버가 값 추가/개명 시 즉시 red.
4. **명령 응답 shape 일관성**: open/register/start/cancel이 **모두 SessionDetail(§3)**을 반환한다고 가정하고 파싱 → invalidate. 서버가 다른 shape(예 204)면 파싱 실패. 공유 픽스처(B2-T1 ④)로 교차 검증 권장.
5. **시각 직렬화**: `CreateSessionRequest`는 `toUtc().toIso8601String()`(밀리초 `.000Z` 포함) 송신. 서버 파싱 허용 여부 확인.

## test-engineer에게 (골든/유닛 대상 순수 Dart)
- `race_dtos.dart`의 파싱·헬퍼는 순수 함수 — 이미 `race_dtos_test.dart`에 기본 커버(SessionDetail 파싱+폴리라인 디코딩, defaultUploadDeadline, 전이 가드 헬퍼). **폴리라인 상호운용 골든(B2-T2)**은 서버-클라 벡터 대조 소관 — 클라측 `PolylineCodec`(기존, `polyline_codec_test`) 라운드트립 유지 확인만 필요(신규 코드 없음).

---

## 5. 보류·자동검증 불가

- **'레이스 시작' 배선 판단(D-1 재확인 필요)**: 태스크 문구 "start 신호 API 호출 UI까지만"에 따라 **서버 STARTED 신호를 실제 호출하는 버튼**을 구현(참가자·OPEN/RUNNING 한정) + "실제 GPS 트래킹은 M2" 명시 안내. 단 planner 계획/analyst 설계는 "**disabled stub**"으로 기술 — 두 해석이 갈린다. 현 구현은 신호만 보내며 트래킹 미배선(D-1 정신 유지). **STARTED 신호가 실주행 없이 세션을 OPEN→RUNNING 전이시키는 부수효과**가 있으므로, "disabled stub이 맞다"면 `_RaceStartEntry`를 비활성 안내로 축소하면 됨(1곳). planner/qa 확인 요청.
- **지도 실 타일**: 네이버 Client ID 대기 — placeholder(CustomPaint)만. 실 SDK 검증은 Client ID 확보 후(자동검증 불가).
- **카카오 로그인**: 앱 키 대기 — 스텁 유지(B1 격리).
- **prod `API_BASE_URL`**: 도메인 대기 — `config/prod.json`은 placeholder(`https://api.example.com`).
- **폰트**: Pretendard/Space Grotesk 자산 부재 — 시스템 폰트 대체(기존 B1 상태 유지, `AppTypography.record`).
- **dev 시드 코스**: 서버(B2-S1 ⑥) 소관. 클라는 GET courses가 빈 목록이면 "선택할 코스가 없어요" 안내(세션 생성 화면). 서버 시드 없으면 세션 생성 수동 검증 불가 → backend-dev 시드 완료 후 통합 확인.

### 수동 테스트 절차(실기기/에뮬레이터 — 자동검증 밖)
1. `flutter run --dart-define-from-file=config/dev.json` → dev 로그인 → 크루 진입 → "레이스 세션".
2. (서버 시드 코스 존재 전제) 크루장 계정: 세션 만들기 → 코스 선택 → 예정/마감(자동 +12h) → 생성 → 상세 진입.
3. 세션 발행(open) → 다른 멤버 계정으로 참가 신청 → 상세에서 참가자·상태 확인 → 레이스 시작 신호 → "지금 뛰는 중" 표시 확인.
4. prod 빌드(`--dart-define-from-file=config/prod.json`)에서 `/spike`·dev 로그인 미노출 확인.
