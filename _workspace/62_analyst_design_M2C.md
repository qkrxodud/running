# 62 — 도메인 설계·계약 확정안 (M2-C: 결과 표면 — 히스토리·PB·코스 승격·CANCELLED 보존)

> 작성: domain-analyst · 2026-07-05 · 기준: `61_planner_plan_M2C.md`(C1~C8), `42_analyst_design_M2A.md`(Tracking/Ranking·PB 정의·payload 격리 — 유효), `domain-model` 스킬, 계획서 §5.2·§5.3·§5.4, `V1__init.sql`, `docs/contracts/`(conventions v0.1.2·course v0.1·session v0.2.1·track v0.1.1·auth v0.1)
> M2-A 설계와 충돌 없음 — 본 문서는 결과 조회·승격·CANCELLED 보존 델타. 신규 서버 조회 경로(C4·C6)는 **payload 격리 유지**(track_record 스캔만) 재검증 트리거.

---

## 0. 오케스트레이터 확정 반영 + 위임 판단 4건

### 반영(오케스트레이터 확정)
- **O-M2C-2**: 히스토리 **전체 노출**(DNF 포함 — 기록 보존 정책 정합), **PB 뱃지는 완주만**. → §2에 반영. **DNS는 track_record 부재(안 뜀)라 기록 히스토리에서 자연 부재**(뛴 기록만 노출) — "전체 노출"은 뛴 기록 전체(FINISHED+DNF) 의미로 해석.
- **O-M2C-4**: 정제 파라미터 제안값(accuracy 50 / speed 12 / window 3 / gap 30) **확정 유지**(42 §3.2 → 승인). 외부화 그대로.

### 확정(위임 판단 — 계획서 근거)

| # | 질문 | 확정 | 근거 |
|---|---|---|---|
| **O-M2C-1** | CANCELLED 개인기록 의미론 | **§1 상세 — 업로드 수락 O / session_id 유지(논리적 세션무관) / FinishPolicy 정상 / 히스토리 노출(배지) / 순위·PB 제외** | 계획서 §5.2 "뛰던 트랙은 개인 기록(세션 무관 주행)으로 보존 — 뛴 노력이 증발하지 않게". 거부하면 클라 완주 트랙의 서버 잔류 경로 소멸 = 규범 위반 |
| **승격 권한(C6)** | 코스 승격은 누구? | **크루 ACTIVE 멤버 누구나, 단 본인 FINISHED 트랙만.** 자유생성(폴리라인 직수신)은 크루장 유지 | course-api CO-B4 "생성=크루장"의 목적은 *세션 발행 재료 관리*. 승격은 *개인이 실주행한 검증 경로를 크루 풀에 기여* — 제품 가치("실주행 기반 코스")의 핵심. 크루장 독점은 이 가치 훼손. **별도 유스케이스에 멤버 권한 부여**(생성 권한 확장 아님) |
| **거리 하한(O-M2C-3)** | 승격 최소 거리 | **`promotion.min_distance_m = 1000`(1km, 외부화)** | ① 1km 미만은 코스 레이스 의미 희박(도시 블록). ② FinishPolicy 코리도 50m·완주 90% 판정이 노이즈에 안 묻히려면 코스 거리 ≥ 코리도×20 = 1km. ③ 관대 하한 — 정상 러닝(3~5K) 전량 통과, 테스트/산책 트랙만 배제. **코스 불변**이라 오염이 되돌릴 수 없어 최소 게이트 필수 |
| **kapi 장애 코드** | kapi 서버 장애 표현 | **`503 AUTH_KAKAO_UNAVAILABLE` 신설**(401 유지와 분리) | 401=*당신 자격 문제*(클라 행동=재로그인/재입력), 503=*우리 의존성 일시 장애*(클라 행동=잠시 후 재시도). 401로 합치면 사용자 잘못 아닌데 무한 재로그인 루프·오귀책 UX. HTTP 503(upstream 장애) 의미론 정확 |

---

## 1. CANCELLED 세션 개인기록 보존 의미론 (C7 — O-M2C-1 확정)

계획서 §5.2 규범을 4개 구현 결정으로 확정:

### 1.1 업로드 수락 여부 — **수락한다**(track-api §1 개정)
- M2-A track-api §1은 CANCELLED를 409로 거부했으나 **개정**: `CANCELLED` 세션도 트랙 **업로드 수락**. 클라는 로컬 우선 저장으로 완주 트랙을 보유 → 서버에 남길 경로가 있어야 "노력 증발 없음" 규범 성립.
- 단 **세션 순위·PB 산정 트리거 없음**(CANCELLED은 RaceResult 미생성 — 계획서 §5.2). TrackUploaded 이벤트가 전원완료 재평가를 하되 CANCELLED이면 FINALIZING 전이 안 함(no-op).
- 여전히 거부: `COMPLETED`(결과 확정 후 — 불변)·`DRAFT`(미발행). 수락: `OPEN|RUNNING|FINALIZING|CANCELLED`.

### 1.2 session_id 처리 — **원 session_id 유지**(물리), 논리적으로 "세션 무관"
- `track_record.session_id`는 V1 NOT NULL·FK → 참조를 끊을 수 없다. **원 세션 참조 유지**(무결성).
- "세션 무관 개인 기록"은 **논리 의미**: 결과·순위·PB **집계에서 CANCELLED 세션 트랙 제외**. 물리적 session_id NULL화 아님.
- 판별: `race_session.status = CANCELLED`인 세션의 track_record → "개인 기록" 취급(rank_entry 없음).

### 1.3 FinishPolicy — **정상 수행**
- CANCELLED라도 코스는 존재(course_id 유효) → 업로드 시 정제·FinishPolicy 정상 실행 → `finish_status`(FINISHED/DNF)·거리·시간 확정. 개인 기록의 완주여부·거리·페이스가 유효(히스토리·승격 소스로 사용 가능).

### 1.4 히스토리 노출 — **노출 + "취소된 세션" 배지**(O-M2C-2 정합)
- 개인 기록 히스토리(§2)에 포함하되 `session_cancelled: true` 플래그 표기. **rank·is_pb 없음**(null/false). 승격 소스로도 사용 가능(본인 FINISHED면).

### 1.5 불변식
| # | 불변식 | 강제 |
|---|---|---|
| CX-1 | CANCELLED 세션 트랙 업로드 수락, 순위·PB 미산정 | track-api §1 개정 + C7 코드 |
| CX-2 | session_id 유지(물리), 집계 제외(논리) | C4 쿼리 — CANCELLED 세션 rank_entry 없음 |
| CX-3 | 삭제하지 않음(탈퇴 시에만 payload 삭제, record 익명 보존) | 기존 Eraser 경로(TR-5) |

---

## 2. 히스토리·PB 조회 데이터 모델 (C3·C4 — payload 격리 유지)

### 2.1 조회 원천 — **track_record 스캔 기반 + rank_entry 조인**(track_payload 조인 0건)
- **기록 히스토리**의 base = `track_record`(user_id=본인) — 모든 실주행(확정·취소 무관)이 여기 존재.
  - LEFT JOIN `race_session`(course·status·scheduled_at) — CANCELLED 배지·코스명.
  - LEFT JOIN `rank_entry`(via `race_result`, 확정 세션만) → rank·is_pb·avg_pace. CANCELLED은 rank_entry 없음 → null.
  - **track_payload 미접근**(블롭 격리 — TR-3 유지, QA 3차 재검증 트리거).
- **DNS 자연 부재**: DNS는 track_record 없음(안 뜀) → 기록 히스토리에 안 나옴(뛴 기록만). O-M2C-2 "전체 노출"=FINISHED+DNF.

### 2.2 PB 목록 — RankingPolicy PB 정의와 **동일 값**
- 유저×`course_id`별 **최소 완주 record_time_s**(확정 세션 rank_entry의 FINISHED만). CANCELLED 트랙 제외(rank_entry 부재)·DNF 제외.
- 42 §5.2 PB 정의(유저×course_id 과거 세션 최소기록)와 일치 — read 모델은 그 값을 재집계할 뿐 새 정의 도입 금지.

### 2.3 권한·shape
- **본인 한정**: `GET /me/records`·`GET /me/personal-bests` — 토큰 sub의 user만. 타인 기록 조회 없음(MVP).
- 페이지네이션 conventions §6 래퍼(records 목록). PB 목록은 코스 수가 작아 페이징 or 단순 배열 — **페이징 래퍼 통일**(규약 일관).
- enum: `finish_status`(FINISHED/DNF) 값집합 명시 — 클라 unknown 폴백(R-001).

### 2.4 불변식
| # | 불변식 | 강제 |
|---|---|---|
| HS-1 | 본인 기록만(토큰 sub) | 코드 — 권한 |
| HS-2 | track_payload 조인 0건 | 포트 분리 + QA 로그(3차 재검증) |
| HS-3 | PB=완주만·유저×course_id 최소·RankingPolicy 정의 동일 값 | C4 쿼리 + 골든 대조 |
| HS-4 | `rank` 예약어 백틱 인용(R-003 이월5) | JPA/네이티브쿼리 인용 |
| HS-5 | CANCELLED 트랙 배지 노출·rank/is_pb null | C4 쿼리 |
| HS-6 | 탈퇴 유저 익명 표시(본인 조회라 해당 적으나 코스명·타참가 없음) | — |

---

## 3. 코스 승격 흐름 (C6 — 승격 권한·불변 원칙 확정)

### 3.1 흐름
```
내 기록 히스토리(§2, FINISHED 트랙) ─▶ "코스로 만들기"(승격 UI)
  ─▶ POST /crews/{crewId}/courses/promote {source_track_record_id, name}
  ─▶ 서버: ① 본인 트랙? ② finish_status=FINISHED? ③ total_distance_m ≥ 1000?
          ④ payload 전용 포트로 refined_payload 로드(재정제 경로 — 격리 예외 허용)
          ⑤ refined 폴리라인 → distance_m 서버확정(CO-B3) → 새 Course 발행(불변)
  ─▶ 201 CourseDetail (크루 소유·created_by=본인·불변)
```

### 3.2 권한·검증 결정
- **권한: 크루 ACTIVE 멤버 누구나, 본인 트랙만.** (자유생성 폴리라인 직수신=크루장 유지 — course-api §1 무변경). 승격은 별도 유스케이스.
- **소스: 본인 FINISHED 트랙만.** DNF·타인 트랙 거부. 근거: DNF는 경로 미완/이탈 가능 → 코스 품질 미보장.
- **거리 하한: `promotion.min_distance_m = 1000`(외부화).** 미달 거부.
- **distance_m: 서버가 refined 폴리라인에서 재계산·확정**(CO-B3 — 클라·track_record 값 불신 아닌 refined 좌표 기준 재계산). start/finish = refined 트랙 양 끝점.
- **불변 원칙: 승격 Course도 불변 애그리거트**(생성만, 수정/삭제 API 부재 — course-api 규약 그대로).
- **payload 접근**: 승격은 refined_payload를 읽어야 하므로 **payload 전용 포트 사용**(리플레이·재정제와 동급의 격리 예외 경로). 조회 API가 아니므로 TR-3(조회 payload 조인 0) 위반 아님 — 승격 유스케이스는 명시적 payload 소비자로 분류.

### 3.3 불변식
| # | 불변식 | 강제 |
|---|---|---|
| PR-1 | 크루 ACTIVE 멤버·본인 트랙만 | 코드 — 권한 |
| PR-2 | FINISHED 트랙만 승격 | 코드 → 409 |
| PR-3 | total_distance_m ≥ promotion.min_distance_m(1000, 외부화) | 코드 → 409 |
| PR-4 | distance_m·start/finish 서버가 refined에서 확정(CO-B3) | 코드 |
| PR-5 | 승격 Course 불변(수정/삭제 API 부재) | 구조 |
| PR-6 | payload는 전용 포트로만 접근(승격=명시적 payload 소비자) | 포트 주입 경계 |

---

## 4. 화면 데이터 요구 (1a 라임 디자인 참조 지점)

> 디자인 기준: `app/docs/design/러닝크루_앱_최종_1a_라임.dc.html`. 각 화면은 해당 섹션 준거(flutter-dev가 dc.html 대조).

### 4.1 세션 결과 화면 (C1) — `GET /sessions/{id}/result` 소비(신규 API 불요)
- 필요 데이터: entries(rank·nickname·status·record_time_s·avg_pace_s_per_km·total_distance_m·is_pb). **완주 rank 오름차순 → DNF → DNS 하단**.
- 렌더 규칙: 동률 공동순위(1,1,3) 그대로 표시 / PB 뱃지=`is_pb` true만 / DNF·DNS는 rank 미표기(하단) / **DNS는 record·pace·distance 키가 null**(P46-1 — 키부재/null 안전 렌더 필수).
- 참조: 1a 라임 "레이스 결과/순위" 섹션.

### 4.2 결과 대기 화면 (C2) — `ResultQueryOutcome.ResultPending`(409 RESULT_NOT_READY)
- "업로드 완료 — 전원 완료 대기 중 (n/m명)". n=업로드 수, m=참가자 수. 폴링/재조회 후 Ready 전환.
- **주의**: 실 트래킹 없이 서버 상태만 소비(기기 무관). **레이스 진행 화면과 혼동 금지**(진행 화면은 게이트 뒤).
- 참조: 1a 라임 "결과 대기/업로드 완료" 상태.

### 4.3 기록 히스토리·PB 화면 (C5) — `GET /me/records`·`GET /me/personal-bests`(신규 C4)
- 히스토리: 세션별 항목(코스명·일시·완주여부·기록·평균페이스·순위·PB뱃지·**취소된 세션 배지**). 빈 기록 상태·페이징.
- PB: 코스별 최고기록 카드(코스명·distance_m·best_record·pace·달성일). PB 뱃지=완주만.
- 승격 진입점: 히스토리의 FINISHED 항목에서 "코스로 만들기"(C6). DNF·취소세션 항목엔 버튼 미노출(PR-2).
- 참조: 1a 라임 "내 기록/히스토리·개인 최고기록" 섹션.

---

## 5. 계약 산출

- **`docs/contracts/history-api.md` 신규 v0.1**: `GET /me/records`(§1)·`GET /me/personal-bests`(§2). 본인 한정, 페이징 래퍼, finish_status enum·CANCELLED 배지·에러 전수.
- **`docs/contracts/course-api.md` v0.1 → v0.2**: §4 승격 엔드포인트 append(`POST /crews/{crewId}/courses/promote`). 멤버 권한·본인 FINISHED·거리 하한·서버 distance 재확정·`COURSE_PROMOTION_INELIGIBLE` 코드.
- **`docs/contracts/track-api.md` v0.1.1 → v0.1.2**: §1 CANCELLED 업로드 **수락**으로 개정(수용 상태에 CANCELLED 추가, 순위·PB 미트리거 명시), §3 결과 조회는 CANCELLED 세션에 RESULT_NOT_READY 아닌 별도 처리(결과 미생성 — 404/409 명시).
- **`docs/contracts/auth-api.md` v0.1 → v0.1.1**: §1 오류에 `503 AUTH_KAKAO_UNAVAILABLE` 추가, §4 스텁 문구 최신화(삭제→프로필 분기 공존, 스텁은 kapi 미호출이라 503 미발생).
- **`docs/contracts/conventions.md` v0.1.2 → v0.1.3**: §4 상태 매핑에 `503 SERVICE_UNAVAILABLE`(의존성 일시 장애) 추가, code 집합에 `AUTH_KAKAO_UNAVAILABLE`·`COURSE_PROMOTION_INELIGIBLE` 추가.

---

## 6. QA 검증 포인트 (payload 격리 3차 재검증 + M2-C 신규)

| 항목 | 대상 |
|---|---|
| **track_payload 격리 3차 재검증**(신규 조회 C4·승격 C6) | HS-2(히스토리 조인 0) + PR-6(승격은 전용 포트 명시 소비자). C4 조회 SQL 로그 payload 부재, C6은 전용 포트만 |
| PB 값 = RankingPolicy 정의 일치 | HS-3 — C4 집계값 vs 42 §5.2 골든 대조 |
| CANCELLED 트랙 보존·배지·집계 제외 | CX-1/2, HS-5 — 취소 후 히스토리 노출·순위 미생성 |
| 승격 자격 게이트(본인·FINISHED·1km) | PR-1/2/3 — DNF·타인·단거리 거부 |
| 결과 화면 null/키부재 안전 렌더 | P46-1 — DNS 항목 |
| 공유 픽스처 CI(P26-2 종결) | C8 — result/track-me/history 실응답 바이트 픽스처 |
| `rank` 예약어 인용 | HS-4(C4), R-003 이월5 |
| 승격 Course 불변·distance 서버확정 | PR-4/5, CO-B3 |
| 503 vs 401 분기(kapi 장애) | auth §1 — 클라 재시도 vs 재로그인 |

---

## 7. backend / flutter 주의사항

**backend-dev (2)**:
- C4 히스토리·C6 승격 조회는 **track_record 스캔만** — 히스토리 SQL에 `track_payload` 조인 0건(HS-2, QA 3차 재검증), 승격은 **payload 전용 포트**로만 refined 접근(PR-6, 조회 어댑터에 주입 금지). `rank` 백틱 인용(R-003 이월5).
- CANCELLED 트랙은 **삭제·session_id NULL화 금지** — 원 참조 유지·집계 제외(CX-2). 승격 distance_m·start/finish는 **refined 폴리라인에서 서버 재확정**(CO-B3, track_record 저장값 신뢰 아님). kapi 장애는 `503 AUTH_KAKAO_UNAVAILABLE`(401 아님 — 실 카카오 어댑터 배선 시).

**flutter-dev (2)**:
- 결과 화면(C1): DNS 항목 **rank/record_time_s/avg_pace_s_per_km 키부재 또는 null** 안전 렌더(P46-1) — non-null 가정 금지. 동률 공동순위·PB 뱃지=`is_pb` true만·DNF/DNS 하단. `finish_status` unknown 폴백(R-001).
- 히스토리(C5): **취소된 세션 배지**(session_cancelled) 표기·해당 항목 승격 버튼 미노출. 승격은 **본인 FINISHED 트랙만** 버튼 노출(PR-2). 401(재로그인) vs **503 AUTH_KAKAO_UNAVAILABLE**(잠시 후 재시도) UX 분기.

---

## 8. 미규정 잔여 (에스컬레이션 — 진행은 제안값)

- **승격 GPS 공백 상한**: 61 O-M2C-3이 "GPS 공백 상한 등"도 언급. **제안: v0.2는 거리 하한만**(1km). 공백 상한은 gps_gap_count 기반이나 튜닝 데이터 부족 → 픽스처 축적 후 도입(범위 밖 표기). 이견 시 회신.
- **CANCELLED 트랙 승격 허용 여부**: 취소 세션의 본인 FINISHED 트랙도 승격 가능? **제안: 허용**(트랙 품질은 완주·거리로 판정, 세션 취소는 코스 품질과 무관). PR-2/3 충족하면 CANCELLED 소스도 승격 OK. 이견 시 회신.
- **타인 기록 조회(리더보드형)**: MVP 본인 한정(HS-1). 크루 전체 히스토리는 2차(범위 밖).
