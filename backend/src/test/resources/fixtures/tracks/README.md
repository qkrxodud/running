# 트랙 골든 픽스처

골든 테스트(순수 함수 회귀 방지선)가 로드하는 고정 입력 트랙. 포맷은 **업로드 계약(`docs/contracts/track-api.md` §0/§1) shape** 와 동일 — 픽스처가 계약 예시 문서를 겸한다.

## 디렉토리 규약

```
fixtures/tracks/
├── synthetic/     # 손계산 가능한 최소 합성 트랙(경계·계약 예시). 5~15점.
└── real/          # 실주행 원시 트랙(재정제·회귀의 원천). ← 사용자 실기기 테스트 대기
```

- **synthetic/**: 자오선(경도 고정, 위도만 변화) 직선을 기본으로 삼는다. 자오선 위 하버사인 거리는 `R·Δlat`(R=6,371,000m)로 **정확히 손계산**되며, 좌표를 1e5 배수(예: 0.0009°=90 units)로 두면 폴리라인 인코딩이 **무손실**이다. 각 파일 `_meta.expected_after_refine` 에 정제 파이프라인(설계 42 §3.1)·완주 판정(§4.1)에서 도출한 기대값을 박제한다.
- **real/**: 실주행 트랙이 들어오면 파일명 `날짜_기기_코스.json`(예: `2026-07-XX_galaxy-s24_han-river-5k.json`)으로 보존하고, 내부 `_meta` 에 유래(기기, 날짜, 특이사항)를 남긴다. 정제 결과를 검토·승인한 뒤 골든으로 편입한다 — 정제 알고리즘 튜닝 시 이 트랙들이 회귀 방지선이다. **현재 비어 있음(실기기 테스트 M2 대기).**

## 현재 synthetic 픽스처

| 파일 | 점수 | 판정 | 용도 |
|------|------|------|------|
| `completed_run_lingers_at_finish.json` | 13 | FINISHED | 정상 완주 경로. 결승선 정지 3점(끝점 스무딩 수축 대비) |
| `shortcut_dnf_6pt.json` | 6 | DNF | 지름길(거리·도착 미충족)이어도 트랙 보존 |
| `gap_run_completed.json` | 13 | FINISHED | GPS 공백(70s Δt) 1개가 있어도 완주 성립 |

로더: `com.runningcrew.tracking.domain.TrackTestFixtures#loadUpload(name)` — 폴리라인 디코딩 + 병렬 배열 zip → `List<TrackPoint>`.
