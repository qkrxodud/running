/// history-api.md v0.1 계약 DTO — 내 기록 히스토리·코스별 PB(순수 Dart).
///
/// - `finish_status` enum 은 **track_dtos 의 `FinishStatus`({FINISHED, DNF}) 재사용**
///   (M2-B0 자산). 히스토리엔 DNS 부재(track_record 없음 — "뛴 기록만").
/// - **키 부재=null(P46-1)**: DNF·CANCELLED 세션 항목은 rank/record_time_s/
///   avg_pace_s_per_km 키가 생략된다 → `json['x'] as T?` 로 안전 파싱.
/// - **avg_pace_s_per_km 필드명 고정(P46-2)**.
library;

import 'track_dtos.dart' show FinishStatus;

/// GET /api/v1/me/records items[] (history-api §1). 완주·DNF 전체(뛴 기록만).
class RecordHistoryItem {
  const RecordHistoryItem({
    required this.trackRecordId,
    required this.sessionId,
    required this.courseId,
    required this.courseName,
    required this.scheduledAt,
    required this.finishStatus,
    required this.rank,
    required this.recordTimeS,
    required this.totalDistanceM,
    required this.avgPaceSPerKm,
    required this.isPb,
    required this.sessionCancelled,
  });

  /// 코스 승격 소스 참조(course-api §4 source_track_record_id).
  final int trackRecordId;
  final int sessionId;
  final int courseId;
  final String courseName;
  final DateTime scheduledAt;
  final FinishStatus finishStatus;

  /// 확정 세션 완주자만. DNF·**CANCELLED 세션 null**(키 부재 포함).
  final int? rank;
  final int? recordTimeS;

  /// 정제 후 거리. DNF 도 뛴 만큼(일반 비null).
  final int? totalDistanceM;
  final int? avgPaceSPerKm;

  /// 완주만 PB 후보. DNF·CANCELLED 세션 항상 false.
  final bool isPb;

  /// true 면 "취소된 세션" 배지(개인 기록 보존 — 계획서 §5.2). rank/is_pb 없음.
  final bool sessionCancelled;

  /// 완주 트랙만 코스 승격 가능(course-api §4 PR-2). DNF·취소 항목 버튼 미노출.
  bool get canPromote => finishStatus == FinishStatus.finished;

  factory RecordHistoryItem.fromJson(Map<String, dynamic> json) =>
      RecordHistoryItem(
        trackRecordId: json['track_record_id'] as int,
        sessionId: json['session_id'] as int,
        courseId: json['course_id'] as int,
        courseName: json['course_name'] as String? ?? '',
        scheduledAt: DateTime.parse(json['scheduled_at'] as String),
        finishStatus: FinishStatus.parse(json['finish_status'] as String?),
        // 아래 4필드 키 부재 가능(P46-1).
        rank: json['rank'] as int?,
        recordTimeS: json['record_time_s'] as int?,
        totalDistanceM: json['total_distance_m'] as int?,
        avgPaceSPerKm: json['avg_pace_s_per_km'] as int?,
        isPb: json['is_pb'] as bool? ?? false,
        sessionCancelled: json['session_cancelled'] as bool? ?? false,
      );
}

/// GET /api/v1/me/personal-bests items[] (history-api §2). 완주 코스별 최소 기록.
class PersonalBestItem {
  const PersonalBestItem({
    required this.courseId,
    required this.courseName,
    required this.distanceM,
    required this.bestRecordTimeS,
    required this.avgPaceSPerKm,
    required this.achievedSessionId,
    required this.achievedAt,
  });

  final int courseId;
  final String courseName;
  final int distanceM;
  final int bestRecordTimeS;
  final int avgPaceSPerKm;
  final int achievedSessionId;
  final DateTime achievedAt;

  factory PersonalBestItem.fromJson(Map<String, dynamic> json) =>
      PersonalBestItem(
        courseId: json['course_id'] as int,
        courseName: json['course_name'] as String? ?? '',
        distanceM: json['distance_m'] as int? ?? 0,
        bestRecordTimeS: json['best_record_time_s'] as int? ?? 0,
        avgPaceSPerKm: json['avg_pace_s_per_km'] as int? ?? 0,
        achievedSessionId: json['achieved_session_id'] as int,
        achievedAt: DateTime.parse(json['achieved_at'] as String),
      );
}
