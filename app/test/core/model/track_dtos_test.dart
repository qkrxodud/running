import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/geo/polyline_codec.dart';
import 'package:running/core/model/enum_codec.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/core/tracking/track_point.dart';

/// track-api.md v0.1.1 DTO — 계약 값집합 대조(R-001)·키부재=null(P46-1)·
/// avg_pace_s_per_km 필드명 고정(P46-2)·폴리라인 1e5 왕복.
void main() {
  group('계약 enum 값 집합 == wire 집합 (R-001 유형 대조)', () {
    test('processing_status = {PROCESSED} (track-api §1)', () {
      expect(ProcessingStatus.wireValues.keys.toSet(), {'PROCESSED'});
    });
    test('finish_status = {FINISHED, DNF} (track-api §1)', () {
      expect(FinishStatus.wireValues.keys.toSet(), {'FINISHED', 'DNF'});
    });
    test('result entry status = {FINISHED, DNF, DNS} (track-api §3)', () {
      expect(ResultEntryStatus.wireValues.keys.toSet(),
          {'FINISHED', 'DNF', 'DNS'});
    });
  });

  group('enum 미지값 폴백 (크래시 금지)', () {
    test('미지 finish_status → unknown + 로깅', () {
      final observed = <String>[];
      EnumParseLog.onUnknown = (ctx, raw) => observed.add('$ctx=$raw');
      addTearDown(() => EnumParseLog.onUnknown = null);

      expect(FinishStatus.parse('CHEATED'), FinishStatus.unknown);
      expect(ProcessingStatus.parse('RECEIVED'), ProcessingStatus.unknown);
      expect(ResultEntryStatus.parse(null), ResultEntryStatus.unknown);
      expect(observed, contains('track.finish_status=CHEATED'));
    });
  });

  group('업로드 요청 — 폴리라인 1e5 + 병렬 배열', () {
    final started = DateTime.utc(2026, 7, 10, 21, 0, 5);
    List<TrackPoint> points() => [
          TrackPoint(
              timestamp: DateTime.utc(2026, 7, 10, 21, 0, 5),
              lat: 37.5665,
              lng: 126.9780,
              altitude: 11.0,
              speed: 0.0,
              accuracy: 12.0),
          TrackPoint(
              timestamp: DateTime.utc(2026, 7, 10, 21, 0, 8),
              lat: 37.5670,
              lng: 126.9785,
              altitude: 11.2,
              speed: 2.8,
              accuracy: 8.5),
        ];

    test('fromPoints: 좌표는 PolylineCodec.encode(1e5) 왕복 일치', () {
      final req = TrackUploadRequest.fromPoints(
        clientUploadId: 'id-1',
        startedAt: started,
        points: points(),
      );
      final decoded = PolylineCodec.decode(req.polyline);
      expect(decoded.length, 2);
      expect(decoded[0].lat, closeTo(37.5665, 1e-5));
      expect(decoded[0].lng, closeTo(126.9780, 1e-5));
      expect(decoded[1].lat, closeTo(37.5670, 1e-5));
    });

    test('timestamps 는 epoch millis(§9 예외), 병렬 배열 길이 N 일치', () {
      final req = TrackUploadRequest.fromPoints(
        clientUploadId: 'id-1',
        startedAt: started,
        points: points(),
      );
      expect(req.timestamps.first, started.millisecondsSinceEpoch);
      final n = PolylineCodec.decode(req.polyline).length;
      expect(req.timestamps.length, n);
      expect(req.speeds.length, n);
      expect(req.accuracies.length, n);
      expect(req.altitudes!.length, n);
    });

    test('toJson: 계약 키 + started_at ISO-8601 UTC + client_meta 3키', () {
      final req = TrackUploadRequest.fromPoints(
        clientUploadId: 'id-1',
        startedAt: started,
        points: points(),
        clientMeta: const ClientMeta(
            os: 'android', osVersion: '14', deviceModel: 'SM-S911N'),
      );
      final json = req.toJson();
      expect(json['client_upload_id'], 'id-1');
      expect(json['started_at'], '2026-07-10T21:00:05.000Z');
      expect((json['timestamps'] as List).first, isA<int>());
      expect(json['client_meta'], {
        'os': 'android',
        'os_version': '14',
        'device_model': 'SM-S911N',
      });
    });

    test('altitude 제외 시 키 생략', () {
      final req = TrackUploadRequest.fromPoints(
        clientUploadId: 'id-1',
        startedAt: started,
        points: points(),
        includeAltitude: false,
      );
      expect(req.toJson().containsKey('altitudes'), isFalse);
    });
  });

  group('TrackRecordResponse — 키 부재=null (P46-1)', () {
    test('완주(FINISHED): 전 필드 존재', () {
      final r = TrackRecordResponse.fromJson({
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
      });
      expect(r.finishStatus, FinishStatus.finished);
      expect(r.finishedAt, DateTime.utc(2026, 7, 10, 21, 26, 41));
      expect(r.totalTimeS, 1596);
    });

    test('DNF: finished_at·total_time_s 키 자체가 생략돼도 null 파싱', () {
      // 전역 NON_NULL — DNF 면 서버가 두 키를 아예 내려보내지 않는다.
      final r = TrackRecordResponse.fromJson({
        'track_record_id': 4022,
        'session_id': 91,
        'user_id': 8,
        'processing_status': 'PROCESSED',
        'finish_status': 'DNF',
        'started_at': '2026-07-10T21:00:05Z',
        'total_distance_m': 3120,
        'gps_gap_count': 0,
        // finished_at / total_time_s 키 없음
      });
      expect(r.finishStatus, FinishStatus.dnf);
      expect(r.finishedAt, isNull);
      expect(r.totalTimeS, isNull);
      expect(r.totalDistanceM, 3120);
    });
  });

  group('ResultResponse — 순위·키부재=null·avg_pace_s_per_km(P46-2)', () {
    final json = {
      'session_id': 91,
      'course': {'id': 55, 'name': '한강 5K', 'distance_m': 5000},
      'finalized_at': '2026-07-11T09:00:03Z',
      'entries': [
        {
          'user_id': 3,
          'nickname': '민수',
          'status': 'FINISHED',
          'rank': 1,
          'record_time_s': 1502,
          'total_distance_m': 5021,
          'avg_pace_s_per_km': 299,
          'is_pb': true,
        },
        {
          // DNS: rank/record_time_s/total_distance_m/avg_pace_s_per_km 키 생략
          'user_id': 9,
          'nickname': '탈퇴한 러너',
          'status': 'DNS',
          'is_pb': false,
        },
      ],
    };

    test('완주 항목: avg_pace_s_per_km 필드명으로 파싱', () {
      final res = RaceResultResponse.fromJson(json);
      expect(res.course.name, '한강 5K');
      final first = res.entries.first;
      expect(first.status, ResultEntryStatus.finished);
      expect(first.rank, 1);
      expect(first.avgPaceSPerKm, 299);
      expect(first.isPb, isTrue);
    });

    test('DNS 항목: 순위·기록 키 부재 → 전부 null, is_pb=false', () {
      final res = RaceResultResponse.fromJson(json);
      final dns = res.entries.last;
      expect(dns.status, ResultEntryStatus.dns);
      expect(dns.rank, isNull);
      expect(dns.recordTimeS, isNull);
      expect(dns.totalDistanceM, isNull);
      expect(dns.avgPaceSPerKm, isNull);
      expect(dns.isPb, isFalse);
    });
  });
}
