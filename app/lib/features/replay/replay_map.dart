import 'package:flutter/material.dart';

import '../../app/app_theme.dart';
import '../../core/geo/lat_lng.dart';
import '../../core/geo/polyline_codec.dart';
import '../../core/model/replay_dtos.dart';
import '../../core/replay/frame_sampler.dart';
import 'replay_palette.dart';

/// placeholder 지도 위 리플레이 렌더 — CoursePolylineMap 패턴 확장(실 타일 없이
/// 좌표 정규화 스케치). 성능을 위해 **정적 배경**(코스·참가자 경로·추월 지점)과
/// **동적 마커**(positionMs 마다 이동)를 2개 레이어로 분리한다 —
/// 배경 painter 는 스냅샷이 같으면 repaint 하지 않아 60fps 애니메이션 부담을 낮춘다.
class ReplayMap extends StatelessWidget {
  const ReplayMap({
    super.key,
    required this.snapshot,
    required this.positionMs,
    this.height = 320,
  });

  final ReplaySnapshot snapshot;
  final int positionMs;
  final double height;

  @override
  Widget build(BuildContext context) {
    final projection = ReplayProjection.fromSnapshot(snapshot);
    return Container(
      height: height,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: const Color(0xFF0E120C),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Stack(
        children: [
          // 정적 배경(코스+경로+추월) — 스냅샷 동일 시 repaint skip.
          Positioned.fill(
            child: RepaintBoundary(
              child: CustomPaint(
                painter: _BackgroundPainter(
                    snapshot: snapshot, projection: projection),
              ),
            ),
          ),
          // 동적 마커 — positionMs 마다 이동.
          Positioned.fill(
            child: CustomPaint(
              painter: _MarkersPainter(
                snapshot: snapshot,
                projection: projection,
                positionMs: positionMs,
              ),
            ),
          ),
          Positioned(
            left: 12,
            top: 12,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.35),
                borderRadius: BorderRadius.circular(100),
              ),
              child: const Text(
                '리플레이 (지도 준비 중)',
                style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: AppColors.muted),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// 좌표 → 위젯 offset 정규화(북쪽 위, 종횡비 유지). 코스 + 전 참가자 프레임 bounds.
class ReplayProjection {
  ReplayProjection(
      {required this.minLat,
      required this.maxLat,
      required this.minLng,
      required this.maxLng});

  final double minLat, maxLat, minLng, maxLng;

  factory ReplayProjection.fromSnapshot(ReplaySnapshot s) {
    double minLat = double.infinity,
        maxLat = -double.infinity,
        minLng = double.infinity,
        maxLng = -double.infinity;
    void acc(double lat, double lng) {
      if (lat < minLat) minLat = lat;
      if (lat > maxLat) maxLat = lat;
      if (lng < minLng) minLng = lng;
      if (lng > maxLng) maxLng = lng;
    }

    for (final p in PolylineCodec.decode(s.course.routePolyline)) {
      acc(p.lat, p.lng);
    }
    for (final part in s.participants) {
      for (final f in part.frames) {
        acc(f.lat, f.lng);
      }
    }
    if (!minLat.isFinite) {
      // 좌표 전무 — 안전 기본(1점).
      minLat = maxLat = 37.5;
      minLng = maxLng = 127.0;
    }
    return ReplayProjection(
        minLat: minLat, maxLat: maxLat, minLng: minLng, maxLng: maxLng);
  }

  Offset project(LatLng p, Size size) {
    const pad = 26.0;
    final spanLat = (maxLat - minLat).abs();
    final spanLng = (maxLng - minLng).abs();
    final w = size.width - pad * 2;
    final h = size.height - pad * 2;
    final sx = spanLng == 0 ? double.infinity : w / spanLng;
    final sy = spanLat == 0 ? double.infinity : h / spanLat;
    var scale = sx < sy ? sx : sy;
    if (!scale.isFinite) scale = 1.0;
    final cx = (p.lng - (minLng + maxLng) / 2) * scale + size.width / 2;
    final cy = -(p.lat - (minLat + maxLat) / 2) * scale + size.height / 2;
    return Offset(cx, cy);
  }
}

/// 정적: 코스(흐린) + 참가자 경로(구간 페이스 색·유실 점선) + 추월 지점.
class _BackgroundPainter extends CustomPainter {
  _BackgroundPainter({required this.snapshot, required this.projection});

  final ReplaySnapshot snapshot;
  final ReplayProjection projection;

  @override
  void paint(Canvas canvas, Size size) {
    // 코스 배경(흐린 라인).
    final course = PolylineCodec.decode(snapshot.course.routePolyline);
    if (course.length >= 2) {
      final path = Path()
        ..moveTo(projection.project(course.first, size).dx,
            projection.project(course.first, size).dy);
      for (final p in course.skip(1)) {
        final o = projection.project(p, size);
        path.lineTo(o.dx, o.dy);
      }
      canvas.drawPath(
        path,
        Paint()
          ..color = const Color(0xFF2A3020)
          ..style = PaintingStyle.stroke
          ..strokeWidth = 8
          ..strokeCap = StrokeCap.round
          ..strokeJoin = StrokeJoin.round,
      );
    }

    // 참가자 경로 — 프레임 간 엣지를 페이스 버킷 색으로, 유실 구간은 점선.
    for (final part in snapshot.participants) {
      _paintParticipantPath(canvas, size, part);
    }

    // 추월 지점(정적 마킹) — 추월한 사람의 t_ms 위치에 마킹.
    for (final o in snapshot.overtakes) {
      final passer = _find(o.passerUserId);
      if (passer == null) continue;
      final sampled = ReplayFrameSampler.sampleAt(passer.frames, o.tMs);
      if (sampled == null) continue;
      final pos = projection.project(sampled.position, size);
      canvas.drawCircle(pos, 9,
          Paint()..color = AppColors.lime.withValues(alpha: 0.25));
      canvas.drawCircle(
        pos,
        5,
        Paint()
          ..color = AppColors.lime
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2,
      );
    }
  }

  void _paintParticipantPath(
      Canvas canvas, Size size, ReplayParticipant part) {
    final frames = part.frames;
    if (frames.length < 2) return;
    for (var i = 0; i < frames.length - 1; i++) {
      final a = frames[i];
      final b = frames[i + 1];
      final oa = projection.project(a.position, size);
      final ob = projection.project(b.position, size);
      // 유실 보간 구간(도착 프레임 is_gap) → 점선·muted 로 실측과 구분.
      if (b.isGap) {
        _dashedLine(canvas, oa, ob,
            Paint()
              ..color = ReplayPalette.gap
              ..style = PaintingStyle.stroke
              ..strokeWidth = 3);
        continue;
      }
      final midDist = (a.cumDistM + b.cumDistM) ~/ 2;
      final color = ReplayPalette.forPaceBucket(_bucketAt(part, midDist));
      // DNF 경로도 표시(기록 보존) — 다만 흐리게.
      final alpha = part.isDnf ? 0.55 : 0.9;
      canvas.drawLine(
        oa,
        ob,
        Paint()
          ..color = color.withValues(alpha: alpha)
          ..style = PaintingStyle.stroke
          ..strokeWidth = part.isDnf ? 2.5 : 4
          ..strokeCap = StrokeCap.round,
      );
    }
  }

  /// 누적거리 [dist] 가 속한 세그먼트의 color_bucket(없으면 0).
  int _bucketAt(ReplayParticipant part, int dist) {
    for (final s in part.segments) {
      if (dist >= s.startDistM && dist < s.endDistM) return s.colorBucket;
    }
    return part.segments.isEmpty ? 0 : part.segments.last.colorBucket;
  }

  void _dashedLine(Canvas canvas, Offset a, Offset b, Paint paint) {
    const dash = 6.0, gap = 4.0;
    final total = (b - a).distance;
    if (total == 0) return;
    final dir = (b - a) / total;
    var d = 0.0;
    while (d < total) {
      final start = a + dir * d;
      final end = a + dir * (d + dash).clamp(0, total).toDouble();
      canvas.drawLine(start, end, paint);
      d += dash + gap;
    }
  }

  ReplayParticipant? _find(int userId) {
    for (final p in snapshot.participants) {
      if (p.userId == userId) return p;
    }
    return null;
  }

  @override
  bool shouldRepaint(_BackgroundPainter old) =>
      !identical(old.snapshot, snapshot);
}

/// 동적: 각 참가자 현재 위치 마커(정체성 색) + 추월 발생 순간 강조.
class _MarkersPainter extends CustomPainter {
  _MarkersPainter({
    required this.snapshot,
    required this.projection,
    required this.positionMs,
  });

  final ReplaySnapshot snapshot;
  final ReplayProjection projection;
  final int positionMs;

  /// 추월 강조 시간창(ms).
  static const int _overtakeWindowMs = 900;

  @override
  void paint(Canvas canvas, Size size) {
    // 추월 발생 순간 강조(지도 위).
    for (final o in snapshot.overtakes) {
      if ((positionMs - o.tMs).abs() > _overtakeWindowMs) continue;
      final passer = _find(o.passerUserId);
      if (passer == null) continue;
      final s = ReplayFrameSampler.sampleAt(passer.frames, positionMs);
      if (s == null) continue;
      final pos = projection.project(s.position, size);
      canvas.drawCircle(
          pos, 16, Paint()..color = AppColors.lime.withValues(alpha: 0.30));
    }

    // 참가자 마커.
    for (var i = 0; i < snapshot.participants.length; i++) {
      final part = snapshot.participants[i];
      final s = ReplayFrameSampler.sampleAt(part.frames, positionMs);
      if (s == null) continue;
      final pos = projection.project(s.position, size);
      final color = ReplayPalette.forParticipant(i);

      if (part.isDnf && s.finished) {
        // DNF 종료 지점 — 속이 빈 마커(중단 표시).
        canvas.drawCircle(
          pos,
          6,
          Paint()
            ..color = color
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2.5,
        );
      } else {
        canvas.drawCircle(pos, 7, Paint()..color = color);
        canvas.drawCircle(
          pos,
          7,
          Paint()
            ..color = const Color(0xFF0E120C)
            ..style = PaintingStyle.stroke
            ..strokeWidth = 2,
        );
      }
    }
  }

  ReplayParticipant? _find(int userId) {
    for (final p in snapshot.participants) {
      if (p.userId == userId) return p;
    }
    return null;
  }

  @override
  bool shouldRepaint(_MarkersPainter old) =>
      old.positionMs != positionMs || !identical(old.snapshot, snapshot);
}
