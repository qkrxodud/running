import 'package:flutter/material.dart';

import '../../../app/app_theme.dart';
import '../../../core/geo/lat_lng.dart';
import '../../../core/geo/polyline_codec.dart';

/// 코스 폴리라인 표시 위젯 **추상화**(B2-C3) — 트래킹 격리 패턴 준용.
///
/// 지도 SDK(네이버) 종속을 이 경계 뒤로 격리한다. 상위 화면(코스 미리보기·
/// 세션 상세)은 [CoursePolylineMap] 만 알고, 실제 렌더가 placeholder 인지
/// 네이버 타일인지 모른다 — Client ID 확보 시 어댑터만 갈아끼운다.
///
/// **교체 지점(Client ID 확보 시)**:
///  1. `flutter_naver_map` 을 pubspec 의존성에 추가.
///  2. `naver_course_polyline_map.dart` 에 `NaverCoursePolylineMap
///     extends CoursePolylineMap` 어댑터 작성(타일 + Polyline overlay).
///  3. Android `AndroidManifest.xml` 에 meta-data(Client ID) —
///     주입은 `AppConfig.naverMapClientId`(B2-C4 dart-define) 경유.
///  4. 아래 [CoursePolylineMap.forCourse] 팩토리를
///     `AppConfig.naverMapReady ? NaverCoursePolylineMap(...) : _Placeholder...`
///     로 분기(현재는 항상 placeholder).
///
/// R-002 가드: 이 파일은 `lib/features` 에 있고 `lib/core` 를 오염시키지 않는다.
/// 폴리라인 디코딩은 core `PolylineCodec` 재사용(신규 core 의존 없음).
abstract class CoursePolylineMap extends StatelessWidget {
  const CoursePolylineMap({
    super.key,
    required this.points,
    this.start,
    this.finish,
    this.height = 200,
  });

  /// 디코딩된 좌표열(지도 위젯은 좌표만 받음 — 인코딩·거리 계산은 core 소관).
  final List<LatLng> points;
  final LatLng? start;
  final LatLng? finish;
  final double height;

  /// 인코딩 폴리라인 → 미리보기 위젯. 현재는 placeholder 반환.
  /// Client ID 확보 시 여기서 네이버 어댑터로 분기(위 §4 교체 지점).
  factory CoursePolylineMap.forCourse({
    Key? key,
    required String polyline,
    LatLng? start,
    LatLng? finish,
    double height = 200,
  }) {
    final decoded = PolylineCodec.decode(polyline);
    return _PlaceholderCoursePolylineMap(
      key: key,
      points: decoded,
      start: start,
      finish: finish,
      height: height,
    );
  }
}

/// placeholder 구현 — 실 지도 타일 없이 폴리라인을 CustomPaint 스케치로 렌더.
/// 좌표를 위젯 bounds 로 정규화해 코스 형태만 보여준다(방위·축척 없음).
class _PlaceholderCoursePolylineMap extends CoursePolylineMap {
  const _PlaceholderCoursePolylineMap({
    super.key,
    required super.points,
    super.start,
    super.finish,
    super.height,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: height,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: AppColors.ink,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Stack(
        children: [
          Positioned.fill(
            child: points.length < 2
                ? const _MapUnavailable()
                : CustomPaint(
                    painter: _PolylinePainter(
                      points: points,
                      start: start,
                      finish: finish,
                    ),
                  ),
          ),
          // placeholder 배지 — 실 지도가 아님을 명시(리뷰·사용자 혼동 방지).
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
                '코스 미리보기 (지도 준비 중)',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: AppColors.muted,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _MapUnavailable extends StatelessWidget {
  const _MapUnavailable();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Text(
        '경로 정보 없음',
        style: TextStyle(color: AppColors.muted, fontSize: 13),
      ),
    );
  }
}

/// 좌표열을 위젯 영역에 정규화해 폴리라인·출발/도착 마커를 그린다.
class _PolylinePainter extends CustomPainter {
  _PolylinePainter({required this.points, this.start, this.finish});

  final List<LatLng> points;
  final LatLng? start;
  final LatLng? finish;

  @override
  void paint(Canvas canvas, Size size) {
    const pad = 24.0;
    double minLat = points.first.lat, maxLat = points.first.lat;
    double minLng = points.first.lng, maxLng = points.first.lng;
    for (final p in points) {
      minLat = p.lat < minLat ? p.lat : minLat;
      maxLat = p.lat > maxLat ? p.lat : maxLat;
      minLng = p.lng < minLng ? p.lng : minLng;
      maxLng = p.lng > maxLng ? p.lng : maxLng;
    }
    final spanLat = (maxLat - minLat).abs();
    final spanLng = (maxLng - minLng).abs();
    // 0 나눗셈 방지 + 종횡비 유지용 공통 스케일.
    final w = size.width - pad * 2;
    final h = size.height - pad * 2;
    final scale = () {
      final sx = spanLng == 0 ? double.infinity : w / spanLng;
      final sy = spanLat == 0 ? double.infinity : h / spanLat;
      final s = sx < sy ? sx : sy;
      return s.isFinite ? s : 1.0;
    }();

    Offset project(LatLng p) {
      // 위도는 위쪽이 큰 값 → y 반전. 중앙 정렬.
      final cx = (p.lng - (minLng + maxLng) / 2) * scale + size.width / 2;
      final cy = -(p.lat - (minLat + maxLat) / 2) * scale + size.height / 2;
      return Offset(cx, cy);
    }

    final path = Path()..moveTo(project(points.first).dx, project(points.first).dy);
    for (final p in points.skip(1)) {
      final o = project(p);
      path.lineTo(o.dx, o.dy);
    }

    final line = Paint()
      ..color = AppColors.lime
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeJoin = StrokeJoin.round
      ..strokeCap = StrokeCap.round;
    canvas.drawPath(path, line);

    final startPt = start ?? points.first;
    final finishPt = finish ?? points.last;
    _marker(canvas, project(startPt), AppColors.accentCyan);
    _marker(canvas, project(finishPt), AppColors.accentPink);
  }

  void _marker(Canvas canvas, Offset o, Color color) {
    canvas.drawCircle(o, 6, Paint()..color = color);
    canvas.drawCircle(
      o,
      6,
      Paint()
        ..color = AppColors.ink
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2,
    );
  }

  @override
  bool shouldRepaint(_PolylinePainter old) =>
      old.points != points || old.start != start || old.finish != finish;
}
