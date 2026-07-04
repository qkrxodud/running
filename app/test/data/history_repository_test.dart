import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/data/course_repository.dart';
import 'package:running/data/history_repository.dart';

/// history-api·course-api §4(promote) HTTP 매핑 — 목킹(서버 불요).
void main() {
  ({Dio dio, List<RequestOptions> sent}) build(
      ResponseBody Function(RequestOptions o) responder) {
    final sent = <RequestOptions>[];
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))
      ..httpClientAdapter = _ProgrammableAdapter((o) {
        sent.add(o);
        return responder(o);
      });
    return (dio: dio, sent: sent);
  }

  ResponseBody json(Object body, int status) => ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );

  group('HttpHistoryRepository', () {
    test('myRecords — 페이지 래퍼 파싱, DNF 키부재 null-safe', () async {
      final h = build((o) => json({
            'items': [
              {
                'track_record_id': 4021,
                'session_id': 91,
                'course_id': 55,
                'course_name': '한강 5K',
                'scheduled_at': '2026-07-10T21:00:00Z',
                'finish_status': 'DNF',
                'total_distance_m': 3120,
                'is_pb': false,
                'session_cancelled': false,
              }
            ],
            'page': 0,
            'size': 20,
            'total_elements': 1,
            'total_pages': 1,
          }, 200));
      final repo = HttpHistoryRepository(dio: h.dio);
      final page = await repo.myRecords();
      expect(page.items, hasLength(1));
      expect(page.items.first.finishStatus, FinishStatus.dnf);
      expect(page.items.first.recordTimeS, isNull);
      expect(h.sent.single.path, '/api/v1/me/records');
    });

    test('myPersonalBests — 경로·파싱', () async {
      final h = build((o) => json({
            'items': [
              {
                'course_id': 55,
                'course_name': '한강 5K',
                'distance_m': 5000,
                'best_record_time_s': 1502,
                'avg_pace_s_per_km': 299,
                'achieved_session_id': 91,
                'achieved_at': '2026-07-10T21:00:00Z',
              }
            ],
            'page': 0,
            'size': 20,
            'total_elements': 1,
            'total_pages': 1,
          }, 200));
      final repo = HttpHistoryRepository(dio: h.dio);
      final page = await repo.myPersonalBests();
      expect(page.items.first.bestRecordTimeS, 1502);
      expect(h.sent.single.path, '/api/v1/me/personal-bests');
    });
  });

  group('HttpCourseRepository.promote (course-api §4)', () {
    test('201 → CourseDetail, 경로·body(source_track_record_id·name)', () async {
      final h = build((o) => json({
            'id': 200,
            'crew_id': 12,
            'name': '내가 뛴 한강 코스',
            'route_polyline': '_p~iF~ps|U',
            'distance_m': 5043,
            'start_lat': 37.5,
            'start_lng': 127.0,
            'finish_lat': 37.52,
            'finish_lng': 127.02,
          }, 201));
      final repo = HttpCourseRepository(dio: h.dio);
      final course = await repo.promote(12,
          sourceTrackRecordId: 4021, name: '내가 뛴 한강 코스');
      expect(course.id, 200);
      expect(course.distanceM, 5043, reason: '서버 확정 distance');
      expect(h.sent.single.path, '/api/v1/crews/12/courses/promote');
      expect(h.sent.single.data['source_track_record_id'], 4021);
      expect(h.sent.single.data['name'], '내가 뛴 한강 코스');
    });

    test('409 COURSE_PROMOTION_INELIGIBLE → ApiException(code) — 코드 분기 가능', () async {
      final h = build((o) => json(
          {'code': 'COURSE_PROMOTION_INELIGIBLE', 'message': '거리 미달'}, 409));
      final repo = HttpCourseRepository(dio: h.dio);
      try {
        await repo.promote(12, sourceTrackRecordId: 4021, name: 'x');
        fail('던져야 함');
      } on ApiException catch (e) {
        expect(e.code, 'COURSE_PROMOTION_INELIGIBLE');
        expect(e.statusCode, 409);
      }
    });

    test('403(타인 트랙) → ApiException', () async {
      final h = build((o) => json({'code': 'FORBIDDEN', 'message': ''}, 403));
      final repo = HttpCourseRepository(dio: h.dio);
      await expectLater(
          repo.promote(12, sourceTrackRecordId: 4021, name: 'x'),
          throwsA(isA<ApiException>()));
    });
  });
}

class _ProgrammableAdapter implements HttpClientAdapter {
  _ProgrammableAdapter(this.responder);
  final ResponseBody Function(RequestOptions o) responder;

  @override
  Future<ResponseBody> fetch(RequestOptions options,
          Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async =>
      responder(options);

  @override
  void close({bool force = false}) {}
}
