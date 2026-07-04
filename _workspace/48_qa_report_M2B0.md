# QA 6차 보고 — M2-A(서버)+M2-B0(클라) 트래킹 3자 대조 최종 (2026-07-04)

> 오케스트레이터가 완료 알림 전문에서 복원한 사본 (QA 에이전트 파일 미기록 — 감사 추적용).

## 판정: PASS — 차단 0 / 경고 0 / 참고 2

실행: gradle build(Testcontainers 실 스택) + flutter analyze 0 + flutter test 177. 경계면 불일치 0. R-001·R-002 green 유지.

## 3자 대조 (계약 v0.1.1 ↔ 서버 ↔ 클라 — 전부 일치)
- 필드명 snake_case(avg_pace_s_per_km @JsonProperty 고정 포함), enum 3종 wire값+unknown 폴백, 폴리라인 1e5·half-away-from-zero 왕복 실증, timestamps epoch millis, client_meta 3키(서버 미허용 키 400)

## R-007 — 완전 종결 (잔여 조건 해소, 레지스트리 기록)
- 계약 평가순서 404→403(비멤버)→409(미등록/상태)→409(중복), 서버 가드 통합테스트, 클라 code-only 배타 분기(classifyTrackUploadError) — 3종 모두 isRetryable=false + 로컬 보존

## 부수 확인
- P46-1 실측: 서버 NON_NULL 키 생략 집합 == 클라 픽스처 생략 집합 (필드 단위 동일)
- 로컬 우선: 큐 제거 경로는 purgeSucceeded뿐, 성공 확인 전 삭제 부재
- 상태머신: RaceLocalState 로컬 전용·FINISHED_LOCAL 미전송·markUploaded는 FINISHED_LOCAL에서만
- 멱등: 재시도 동일 client_upload_id 재사용 왕복

## 이월 (비차단)
- P26-2: 단일 바이트 공유 픽스처 파일 미완 (양쪽 필드 집합 실측 일치 확인으로 진전) — test-engineer 승격 권고
- 미규정-A4 정제 파라미터 승인 대기 (경계면 무관)
- M2-B 본체 전량 스파이크 게이트 뒤
