# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 구조

- `app/` — Flutter 클라이언트 (Android 우선). 상세 가이드는 `app/CLAUDE.md`
- `backend/` — Spring Boot 3.x + Java 17 서버 (헥사고날)
- `app/docs/러닝크루_앱_계획서.md` — 제품·도메인·아키텍처의 단일 진실 공급원
- `docs/contracts/` — 앱↔서버 API 계약 (경계면의 기준)
- `_workspace/` — 하네스 중간 산출물 (감사 추적용 보존)

## 하네스: 러닝크루 앱 개발

**목표:** 계획서 기반으로 Flutter 클라이언트 + Spring Boot 백엔드를 6인 에이전트 팀(기획·도메인·백엔드·Flutter·테스트·QA)으로 개발한다.

**트리거:** 러닝크루 앱 관련 개발 작업(기능 구현, 설계, 수정, 재실행, 보완) 요청 시 `running-dev` 스킬을 사용하라. 단순 질문·단일 파일 소규모 수정은 직접 응답 가능.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-07-03 | 초기 구성 (에이전트 6종, 스킬 6종) | 전체 | - |
| 2026-07-03 | Java 17 → 25 변경 (Spring Boot 3.5+ 요구) | agents/backend-dev, skills/backend-hexagonal, 계획서 §6 | 사용자 결정 |
