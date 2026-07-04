# QA 5차 보고 — M2-A 서버 심장부 (2026-07-04)

> 주의: 원본 파일이 QA 에이전트 종료 시 디스크에 남지 않아 **오케스트레이터가 완료 알림 전문에서 복원**한 사본이다 (내용은 알림 원문 그대로, 감사 추적용).

## 판정: PASS — 차단 0 / 경고 2 / 참고 3

실행 검증: `./gradlew build` + cleanTest 강제 재실행 (TrackUploadFinalizationHttpFlowTest E2E 박제 확인 — 스텁 아님), 계약↔서버 2자 대조 + 불변식 코드 수색. 경계면 불일치 0.

## 경고 (레지스트리 등록)
- **W46-1 = R-006**: TK-3 본문 8MiB 바이트 상한 미배선 (포인트 수 20k만 검사) → backend-dev 수정 완료, CLOSED
- **W46-2 = R-007**: track-api §1 오류코드 자기모순 (비참가자 403/409 동시 규정, 서버는 일괄 409 — 크루 경계 누설) → domain-analyst (b)분리 판정·계약 v0.1.1, 서버 멤버십 가드 추가, CLOSED (잔여: M2-B 클라 분기 → M2-B0에서 구현, qa 6차 재검증 대상)

## 참고
- P46-1: 전역 @JsonInclude(NON_NULL) — DNF/DNS 시 nullable 필드 **키 자체 생략**. 클라는 "키 부재=null" 파싱 필수
- P46-2: `avg_pace_s_per_km`는 SNAKE_CASE 전략 오변환 때문에 @JsonProperty로 고정 — 유사 필드 주의
- P46-3: 스케줄러 deadline 통합 E2E 미검증 (SessionClosePolicy는 clock 주입 테스트로 커버, 저위험)

## 이월 종결 (5건)
track_payload 격리(JOIN 0 실증) · R-003 이월5 rank 인용(validate 실증) · O-M2-2/미규정-4 FINALIZING 취소 409 · 미규정-2 OPEN→FINALIZE 허용 · O-M2-4 재업로드 멱등

## M2-B 인계
W46-2 클라 분기(→M2-B0 완료) · P26-2 3자 대조 완성 · P46-1 실응답 왕복 검증 · enum wire 대조(finish_status/status/processing_status) · 폴리라인 1e5 업로드 왕복
