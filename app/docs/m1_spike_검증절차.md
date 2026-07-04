# M1 트래킹 스파이크 — 실기기 검증 절차

> 목적: **프로젝트 성립 조건** 검증 — 화면 끈 채 1시간 이상, Foreground Service만으로(백그라운드 위치 권한 없이) 위치 기록이 유실 없이 로컬에 쌓이는가. (계획서 §8 M1, fail fast)

## 준비

1. 갤럭시 실기기 USB 연결, 개발자 옵션 + USB 디버깅 켜기
2. `cd app && flutter run` (또는 `adb install build/app/outputs/flutter-apk/app-debug.apk`)
3. 앱 실행 → 권한 3종 허용: 위치(앱 사용 중), 알림, 배터리 최적화 예외

## 테스트 1 — 화면 꺼짐 1시간 (실내 가능, 창가 권장)

1. '기록 시작' → 상단 알림에 "러닝 기록 중" 표시 확인
2. 화면 끄고 1시간 이상 방치 (충전기 연결하지 말 것 — 절전 정책이 완화되어 검증이 무의미해짐)
3. 화면 켜서 '기록 중지' → 수집 포인트 수 확인

**판정**: 4초 간격 기준 시간당 이론치 900포인트. **800개 이상(±10% 유실)이면 통과.** 수백 개 수준으로 뚝 떨어져 있으면 절전에 의한 중단 — 아래 "유실 시 확인" 참조.

## 테스트 2 — 실주행 (야외)

1. '기록 시작' → 폰 주머니에 넣고 20분 이상 실제 달리기 (화면 끈 상태)
2. 종료 후 포인트 수와 마지막 정확도 확인

**판정**: 20분 기준 이론치 300포인트, 270개 이상 + 정확도 대체로 20m 이내면 통과.

## 테스트 3 — 절전 극한 상황

- 앱을 최근 앱 목록에서 스와이프로 제거하지 **않은** 상태에서, 배터리 절전 모드(중간 단계) 켜고 30분 방치 → 기록 지속 여부 확인
- 삼성 "앱 자동 종료(딥슬립)" 목록에 앱이 들어가지 않았는지 확인: 설정 → 배터리 → 백그라운드 사용 제한

## 수집 데이터 확인·회수

```bash
# 기기에서 트랙 파일 꺼내기 (analyze용 + 골든 테스트 픽스처 축적)
adb shell run-as com.qkrxodud.runningcrew ls files/
adb shell run-as com.qkrxodud.runningcrew cat "files/spike_track_<파일명>.jsonl" > fixtures/tracks/<날짜>_<기기>_<상황>.jsonl
```

포인트 간격 분석 (터미널):

```bash
jq -r '.ts' <파일>.jsonl | python3 -c "
import sys, datetime
ts=[datetime.datetime.fromisoformat(l.strip()) for l in sys.stdin]
gaps=[(b-a).total_seconds() for a,b in zip(ts,ts[1:])]
print(f'포인트 {len(ts)}개, 평균 간격 {sum(gaps)/len(gaps):.1f}s, 최대 공백 {max(gaps):.0f}s, 10s 초과 공백 {sum(1 for g in gaps if g>10)}회')"
```

## 유실 시 확인 순서 (fail fast 판정 전)

1. 배터리 최적화 예외가 실제로 적용됐는지 (설정 → 앱 → 배터리 → 제한 없음)
2. 알림이 살아있었는지 (알림이 죽었다 = 서비스가 죽었다)
3. 위 두 개가 정상인데도 유실 심각 → **유료 패키지(`flutter_background_geolocation`) 전환 결정** — todolist M1 판정 항목

## 검증 결과 기록

결과는 이 파일 하단에 기록하고, 회수한 트랙은 `fixtures/tracks/`에 커밋한다 (골든 테스트 원료).

| 날짜 | 기기 | 시나리오 | 포인트 수/이론치 | 최대 공백 | 판정 |
|------|------|----------|-----------------|----------|------|
| | | | | | |
