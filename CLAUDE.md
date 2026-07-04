# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 구조

- `app/` — Flutter 클라이언트 (Android 우선). 상세 가이드는 `app/CLAUDE.md`
- `backend/` — Spring Boot 3.5+ + Java 25 서버 (헥사고날)
- `app/docs/러닝크루_앱_계획서.md` — 제품·도메인·아키텍처의 단일 진실 공급원
- `app/docs/design/러닝크루_앱_최종_1a_라임.dc.html` — 화면 디자인 기준 (claude.ai/design 프로젝트 `26e58ccd-…`에서 가져옴)
- `docs/contracts/` — 앱↔서버 API 계약 (경계면의 기준)
- `docs/regressions.md` — 회귀 레지스트리 (모든 버그의 영구 장부, 재발 방지 장치 연결 필수)
- `_workspace/` — 하네스 중간 산출물 (감사 추적용 보존)

## 하네스: 러닝크루 앱 개발

**목표:** 계획서 기반으로 Flutter 클라이언트 + Spring Boot 백엔드를 6인 에이전트 팀(기획·도메인·백엔드·Flutter·테스트·QA)으로 개발한다.

**트리거:** 러닝크루 앱 관련 개발 작업(기능 구현, 설계, 수정, 재실행, 보완) 요청 시 `running-dev` 스킬을 사용하라. 단순 질문·단일 파일 소규모 수정은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-07-03 | 초기 구성 (에이전트 6종, 스킬 6종) | 전체 | - |
| 2026-07-03 | Java 17 → 25 변경 (Spring Boot 3.5+ 요구) | agents/backend-dev, skills/backend-hexagonal, 계획서 §6 | 사용자 결정 |
| 2026-07-03 | 화면 디자인 기준 등록 ("1a 라임" dc.html + 디자인 토큰) | skills/flutter-client, agents/flutter-dev, app/docs/design/ | 사용자 디자인 확정 |
| 2026-07-03 | 회귀 방지 체계 추가 (레지스트리 + red→green 박제 + Phase 5 게이트) | docs/regressions.md, skills/running-dev·golden-testing·qa-integration, agents/test-engineer | 재발 방지 장치 부재 피드백 |
| 2026-07-04 | 배치 A 완료 — 백엔드 골조·클라 아키텍처·계약 v0.1·R-002/R-003 종결. 다음: 배치 B (`_workspace/01_planner_plan.md` 참조, O-1 알림 방식 결정 대기, 카카오 키 대기) | 커밋 779698c | 팀 실행 1회차 |
| 2026-07-04 | 배치 B1 완료 — 인증(JWT+카카오 스텁)·User(탈퇴 6단계 TX)·Crew(승계 불변식) 수직 슬라이스, 클라 dio 개통·화면 7종(1a 라임), QA 3차 3자 대조 PASS(차단·경고 0). O-1=인앱 갈음, O-4=재로그인 신규 User 확정. 다음: B2 (`_workspace/11_planner_plan_B.md` §B2 — Race 컨텍스트·세션 화면·CI) | 커밋 adefd15 | 팀 실행 2회차 |
| 2026-07-04 | 배치 B2 완료 — Race 컨텍스트(Course 불변 격상·상태머신 24셀 골든·register/start/cancel), 세션 화면 3종, 지도 추상화(placeholder, Client ID 대기), dev/prod 분리, CI 구축, 폴리라인 1e5 tie 교차 골든로 상호운용 종결. QA 4차 PASS(차단 0), W26-1 정본=활성 버튼. M1 잔여: 지도 실연동·딥링크·release 서명·Crashlytics(발급물 게이트) + 실기기 스파이크 검증. 다음: M2 트래킹 완성 (`_workspace/21_planner_plan_B2.md` §M2 이관 참조) | 커밋 65842d3 | 팀 실행 3회차 |
| 2026-07-04 | M2-A 완료 — 서버 심장부: 순수함수 4종(정제·완주판정·세그먼트·순위, 임계 전부 외부화) + 업로드 멱등 파이프라인 + 자동 확정(AFTER_COMMIT) + 마감 스케줄러. 골든 카탈로그 49케이스(총 175), R-006/007/008 발견·수정·박제(레지스트리). QA 5차 PASS, track_payload 격리·rank 인용 등 이월 5건 종결. 다음: M2-B 클라 실배선 — **실기기 스파이크 검증이 게이트** (`_workspace/41_planner_plan_M2.md` §M2-B) | 커밋 (본 커밋) | 팀 실행 4회차 |
