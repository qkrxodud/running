import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/core/model/track_error.dart';
import 'package:running/data/track_repository.dart';

/// HttpTrackRepository — 계약 응답/오류 매핑. 403/409 는 code 로만 분기(R-007),
/// RESULT_NOT_READY 는 예외 아닌 대기 상태로 매핑.
void main() {
  ({HttpTrackRepository repo, List<RequestOptions> sent}) build(
      ResponseBody Function(RequestOptions o) responder) {
    final sent = <RequestOptions>[];
    final adapter = _ProgrammableAdapter((o) {
      sent.add(o);
      return responder(o);
    });
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))
      ..httpClientAdapter = adapter;
    return (repo: HttpTrackRepository(dio: dio), sent: sent);
  }

  ResponseBody json(Map<String, dynamic> body, int status) =>
      ResponseBody.fromString(jsonEncode(body), status,
          headers: {
            Headers.contentTypeHeader: ['application/json'],
          });

  group('upload', () {
    test('201 신규 → TrackRecordResponse, 경로·body 확인', () async {
      final h = build((o) => json({
            'track_record_id': 4021,
            'session_id': 91,
            'user_id': 7,
            'processing_status': 'PROCESSED',
            'finish_status': 'FINISHED',
            'started_at': '2026-07-10T21:00:05Z',
            'finished_at': '2026-07-10T21:26:41Z',
            'total_distance_m': 5043,
            'total_time_s': 1596,
            'gps_gap_count': 1,
          }, 201));

      final r = await h.repo.upload(91, _req('id-1'));
      expect(r.trackRecordId, 4021);
      expect(r.finishStatus, FinishStatus.finished);
      expect(h.sent.single.path, '/api/v1/sessions/91/track');
      expect(h.sent.single.data['client_upload_id'], 'id-1');
    });

    test('403 FORBIDDEN → ApiException(code) → classify=forbiddenNotMember', () async {
      final h = build(
          (o) => json({'code': 'FORBIDDEN', 'message': '멤버 아님'}, 403));
      try {
        await h.repo.upload(91, _req('id-1'));
        fail('던져야 함');
      } on ApiException catch (e) {
        expect(e.code, 'FORBIDDEN');
        expect(classifyTrackUploadError(e),
            TrackUploadErrorKind.forbiddenNotMember);
      }
    });

    test('409 SESSION_STATE_INVALID(미등록) → notRegisteredOrBadState', () async {
      final h = build((o) =>
          json({'code': 'SESSION_STATE_INVALID', 'message': '선 register'}, 409));
      try {
        await h.repo.upload(91, _req('id-1'));
        fail('던져야 함');
      } on ApiException catch (e) {
        expect(classifyTrackUploadError(e),
            TrackUploadErrorKind.notRegisteredOrBadState);
      }
    });

    test('409 TRACK_ALREADY_UPLOADED → alreadyUploaded', () async {
      final h = build((o) =>
          json({'code': 'TRACK_ALREADY_UPLOADED', 'message': '이미'}, 409));
      try {
        await h.repo.upload(91, _req('id-1'));
        fail('던져야 함');
      } on ApiException catch (e) {
        expect(classifyTrackUploadError(e),
            TrackUploadErrorKind.alreadyUploaded);
      }
    });
  });

  group('myTrack', () {
    test('404(미업로드) → null (예외 아님)', () async {
      final h =
          build((o) => json({'code': 'NOT_FOUND', 'message': '미업로드'}, 404));
      expect(await h.repo.myTrack(91), isNull);
    });

    test('403 은 rethrow(비멤버는 대기가 아니라 오류)', () async {
      final h = build((o) => json({'code': 'FORBIDDEN', 'message': ''}, 403));
      await expectLater(h.repo.myTrack(91), throwsA(isA<ApiException>()));
    });
  });

  group('result', () {
    test('200 → ResultReady', () async {
      final h = build((o) => json({
            'session_id': 91,
            'course': {'id': 55, 'name': '한강 5K', 'distance_m': 5000},
            'finalized_at': '2026-07-11T09:00:03Z',
            'entries': [],
          }, 200));
      final out = await h.repo.result(91);
      expect(out, isA<ResultReady>());
      expect((out as ResultReady).result.sessionId, 91);
    });

    test('409 RESULT_NOT_READY → ResultPending (전원 완료 대기)', () async {
      final h = build((o) =>
          json({'code': 'RESULT_NOT_READY', 'message': '미확정'}, 409));
      expect(await h.repo.result(91), isA<ResultPending>());
    });

    test('403 은 rethrow', () async {
      final h = build((o) => json({'code': 'FORBIDDEN', 'message': ''}, 403));
      await expectLater(h.repo.result(91), throwsA(isA<ApiException>()));
    });
  });
}

TrackUploadRequest _req(String id) => TrackUploadRequest(
      clientUploadId: id,
      startedAt: DateTime.utc(2026, 7, 10, 21, 0, 5),
      polyline: '_p~iF~ps|U',
      timestamps: const [1752181205000, 1752181208000],
      speeds: const [0.0, 2.8],
      accuracies: const [12.0, 8.5],
    );

/// 요청별 응답을 프로그래밍 가능한 dio 어댑터(HTTP 없이 인터셉터·매핑만 격리).
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
