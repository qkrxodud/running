/// 프레임 보간 — **순수 함수**(IO·시계 없음). 상대 시각 t 에서 참가자의 위치를
/// 프레임 간 선형 보간으로 구한다(마커 동시 이동 애니메이션의 계산 코어).
///
/// 전 참가자가 같은 재생 위치([tMs])를 공유해 동시에 이동한다(고스트 병합).
/// 골든 테스트 대상 — 위젯은 이 결과를 렌더만 한다.
library;

import '../geo/lat_lng.dart';
import '../model/replay_dtos.dart';

/// [tMs] 시점의 샘플 위치.
class SampledPosition {
  const SampledPosition({
    required this.position,
    required this.isGap,
    required this.finished,
  });

  final LatLng position;

  /// 현재 이동 중인 구간이 GPS 유실 보간 구간인가(뷰어 점선 표시).
  final bool isGap;

  /// 마지막 프레임에 도달·초과했는가(완주/DNF 종료 — 마커 정지).
  final bool finished;
}

class ReplayFrameSampler {
  const ReplayFrameSampler._();

  /// [frames](t_ms 비내림차순 정렬 전제)에서 [tMs] 위치를 보간한다.
  /// - frames 비어있음 → null.
  /// - tMs ≤ 첫 프레임 → 시작점(아직 시작 전에도 출발선에 표시).
  /// - tMs ≥ 마지막 프레임 → 마지막점 고정(finished=true).
  /// - 그 외 → 감싸는 두 프레임 선형 보간. isGap = 도착 프레임의 is_gap
  ///   (보간 대상 구간이 유실 구간이면 도착 프레임에 is_gap=true — 스키마 규약).
  static SampledPosition? sampleAt(List<ReplayFrame> frames, int tMs) {
    if (frames.isEmpty) return null;
    final first = frames.first;
    final last = frames.last;
    // 종료(마지막 프레임 도달)를 먼저 판정 — 단일 프레임(first==last)은 finished.
    if (tMs >= last.tMs) {
      return SampledPosition(
          position: last.position, isGap: last.isGap, finished: true);
    }
    if (tMs <= first.tMs) {
      return SampledPosition(
          position: first.position, isGap: first.isGap, finished: false);
    }

    // 이진 탐색: frames[i].tMs <= tMs < frames[i+1].tMs.
    var lo = 0;
    var hi = frames.length - 1;
    while (lo + 1 < hi) {
      final mid = (lo + hi) >> 1;
      if (frames[mid].tMs <= tMs) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    final a = frames[lo];
    final b = frames[lo + 1];
    final span = b.tMs - a.tMs;
    final f = span <= 0 ? 0.0 : (tMs - a.tMs) / span;
    return SampledPosition(
      position: LatLng(
        a.lat + (b.lat - a.lat) * f,
        a.lng + (b.lng - a.lng) * f,
      ),
      isGap: b.isGap,
      finished: false,
    );
  }
}
