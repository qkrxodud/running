import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/race_dtos.dart';

/// course-api.md v0.1 · session-api.md v0.2 응답 shape 파싱 검증.
/// 계약 문서의 예시 JSON 을 그대로 투입 — 서버 실바이트↔앱 파싱 교차의 씨앗.
void main() {
  test('SessionDetail 파싱 + 폴리라인 디코딩 재사용(R-002)', () {
    final json = {
      'id': 91,
      'crew_id': 12,
      'course': {
        'id': 55,
        'name': '한강 5K',
        'distance_m': 5000,
        'route_polyline': '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
        'start_lat': 37.5,
        'start_lng': 127.0,
        'finish_lat': 37.52,
        'finish_lng': 127.02,
      },
      'status': 'RUNNING',
      'scheduled_at': '2026-07-10T21:00:00Z',
      'upload_deadline': '2026-07-11T09:00:00Z',
      'participants': [
        {'user_id': 3, 'nickname': '민수', 'status': 'FINISHED'},
        {'user_id': 7, 'nickname': '지현', 'status': 'STARTED'},
        {'user_id': 9, 'nickname': '탈퇴한 러너', 'status': 'DNS'},
      ],
    };

    final detail = SessionDetail.fromJson(json);
    expect(detail.id, 91);
    expect(detail.status, RaceStatus.running);
    expect(detail.course.name, '한강 5K');
    expect(detail.participants.length, 3);
    expect(detail.hasRunners, isTrue); // STARTED 존재
    // 폴리라인은 core PolylineCodec 재사용 — 좌표 복원.
    expect(detail.course.decodedPath, isNotEmpty);
    // 탈퇴 유저 행 보존(익명 표시).
    expect(detail.participants.last.nickname, '탈퇴한 러너');
    expect(detail.participants.last.status, ParticipationStatus.dns);
  });

  test('SessionSummary·CourseSummary 목록 shape', () {
    final s = SessionSummary.fromJson({
      'id': 91,
      'crew_id': 12,
      'course_id': 55,
      'course_name': '한강 5K',
      'status': 'OPEN',
      'scheduled_at': '2026-07-10T21:00:00Z',
      'upload_deadline': '2026-07-11T09:00:00Z',
      'participant_count': 6,
    });
    expect(s.status, RaceStatus.open);
    expect(s.participantCount, 6);

    final c = CourseSummary.fromJson({
      'id': 55,
      'crew_id': 12,
      'name': '한강 5K',
      'distance_m': 5000,
      'created_at': '2026-07-04T09:00:00Z',
    });
    expect(c.distanceM, 5000);
  });

  test('upload_deadline 기본값 = scheduled + 12h (앱레이어 UX)', () {
    final scheduled = DateTime.utc(2026, 7, 10, 21);
    expect(defaultUploadDeadline(scheduled), DateTime.utc(2026, 7, 11, 9));
  });

  test('CreateSessionRequest 는 UTC ISO8601 로 직렬화', () {
    final req = CreateSessionRequest(
      courseId: 55,
      scheduledAt: DateTime.utc(2026, 7, 10, 21),
      uploadDeadline: DateTime.utc(2026, 7, 11, 9),
    );
    final json = req.toJson();
    expect(json['course_id'], 55);
    expect(json['scheduled_at'], '2026-07-10T21:00:00.000Z');
    expect(json['upload_deadline'], '2026-07-11T09:00:00.000Z');
  });

  test('RaceStatus 전이 가드 헬퍼', () {
    expect(RaceStatus.draft.canOpen, isTrue);
    expect(RaceStatus.open.canRegister, isTrue);
    expect(RaceStatus.draft.canRegister, isFalse);
    expect(RaceStatus.running.canCancel, isTrue);
    expect(RaceStatus.completed.canCancel, isFalse);
    expect(RaceStatus.completed.isTerminal, isTrue);
  });
}
