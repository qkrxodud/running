import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/features/race/map/course_polyline_map.dart';

/// CoursePolylineMap placeholder(B2-C3) — 지도 SDK 없이 폴리라인 렌더.
void main() {
  testWidgets('폴리라인 미리보기 placeholder 가 렌더된다(지도 준비 중 배지)',
      (tester) async {
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: CoursePolylineMap.forCourse(
          polyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
          height: 200,
        ),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.byType(CustomPaint), findsWidgets);
    expect(find.text('코스 미리보기 (지도 준비 중)'), findsOneWidget);
  });

  testWidgets('좌표가 부족하면 경로 정보 없음', (tester) async {
    await tester.pumpWidget(const MaterialApp(
      home: Scaffold(body: SizedBox()),
    ));
    // 빈 폴리라인 → 2점 미만 → 안내.
    await tester.pumpWidget(MaterialApp(
      home: Scaffold(
        body: CoursePolylineMap.forCourse(polyline: '', height: 120),
      ),
    ));
    await tester.pumpAndSettle();

    expect(find.text('경로 정보 없음'), findsOneWidget);
  });
}
