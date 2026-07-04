/// course-api.md v0.1 · session-api.md v0.2 계약 DTO — enum 은 전부 unknown 폴백(R-001 방지).
///
/// enum 값 집합의 진실은 `docs/contracts/`. 미구현 상태값(FINISHED/DNF/DNS 등)도
/// **집합 전체를 보존**해 서버가 그 값을 보내도 크래시하지 않는다(unknown 아님 —
/// 실제 계약 값이므로 정식 멤버). 계약 대조 테스트로 집합 이탈을 즉시 감지한다.
///
/// 폴리라인 디코딩은 `lib/core/geo/PolylineCodec` 재사용 — 여기서 신규 core
/// 의존을 만들지 않는다(R-002).
library;

import '../geo/lat_lng.dart';
import '../geo/polyline_codec.dart';
import 'enum_codec.dart';

/// `race_session.status` (RaceStatus) — session-api.md v0.2 §Enum.
/// {DRAFT, OPEN, RUNNING, FINALIZING, COMPLETED, CANCELLED}. B2 능동 전이는
/// DRAFT/OPEN/RUNNING/CANCELLED. FINALIZING/COMPLETED 는 M2(집합 보존).
enum RaceStatus {
  draft('DRAFT'),
  open('OPEN'),
  running('RUNNING'),
  finalizing('FINALIZING'),
  completed('COMPLETED'),
  cancelled('CANCELLED'),
  unknown(null);

  const RaceStatus(this.wire);
  final String? wire;

  static Map<String, RaceStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static RaceStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: RaceStatus.unknown,
        context: 'race_session.status',
      );

  /// OPEN 세션에만 참가 신청 가능(§8 매트릭스).
  bool get canRegister => this == RaceStatus.open;

  /// 크루장이 취소 가능한 상태(DRAFT/OPEN/RUNNING).
  bool get canCancel =>
      this == RaceStatus.draft ||
      this == RaceStatus.open ||
      this == RaceStatus.running;

  /// DRAFT 만 발행(open) 가능.
  bool get canOpen => this == RaceStatus.draft;

  bool get isTerminal =>
      this == RaceStatus.completed || this == RaceStatus.cancelled;
}

/// `participation.status` (Participation) — session-api.md v0.2 §Enum.
/// {REGISTERED, STARTED, FINISHED, DNF, DNS, WITHDRAWN}. B2 능동 전이는
/// REGISTERED/STARTED. 나머지는 M2·탈퇴 소관이나 값 집합은 전량 보존.
///
/// 주의: 이는 **서버 상태**다. 클라 로컬 상태머신(READY/RUNNING/FINISHED_LOCAL/
/// UPLOADED)과 별개 — 혼동 금지(트래킹 실배선은 M2).
enum ParticipationStatus {
  registered('REGISTERED'),
  started('STARTED'),
  finished('FINISHED'),
  dnf('DNF'),
  dns('DNS'),
  withdrawn('WITHDRAWN'),
  unknown(null);

  const ParticipationStatus(this.wire);
  final String? wire;

  static Map<String, ParticipationStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static ParticipationStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: ParticipationStatus.unknown,
        context: 'participation.status',
      );

  /// "지금 뛰는 중" 표시 기준(B2-C1 ③) — STARTED = 주행 중.
  bool get isRunningNow => this == ParticipationStatus.started;
}

/// GET /crews/{id}/courses items 요소 (course-api.md §2). 경량 — 폴리라인 미포함.
class CourseSummary {
  const CourseSummary({
    required this.id,
    required this.crewId,
    required this.name,
    required this.distanceM,
    required this.createdAt,
  });

  final int id;
  final int crewId;
  final String name;
  final int distanceM;
  final DateTime createdAt;

  factory CourseSummary.fromJson(Map<String, dynamic> json) => CourseSummary(
        id: json['id'] as int,
        crewId: json['crew_id'] as int,
        name: json['name'] as String,
        distanceM: json['distance_m'] as int? ?? 0,
        createdAt: DateTime.parse(json['created_at'] as String),
      );
}

/// GET /courses/{id} (course-api.md §3). SessionDetail.course 요약도 동일 필드 소스.
class CourseDetail {
  const CourseDetail({
    required this.id,
    required this.name,
    required this.routePolyline,
    required this.distanceM,
    required this.start,
    required this.finish,
    this.crewId,
    this.createdBy,
    this.createdAt,
  });

  final int id;

  /// 세션 상세의 course 요약엔 crew_id 부재 가능 — nullable.
  final int? crewId;
  final String name;

  /// 1e5 인코딩 폴리라인 — [decodedPath] 로 좌표 복원(PolylineCodec 재사용).
  final String routePolyline;
  final int distanceM;
  final LatLng start;
  final LatLng finish;
  final int? createdBy;
  final DateTime? createdAt;

  /// 폴리라인 → 좌표열. 지도 위젯 입력(신규 core 의존 없이 기존 코덱 재사용).
  List<LatLng> get decodedPath => PolylineCodec.decode(routePolyline);

  factory CourseDetail.fromJson(Map<String, dynamic> json) => CourseDetail(
        id: json['id'] as int,
        crewId: json['crew_id'] as int?,
        name: json['name'] as String,
        routePolyline: json['route_polyline'] as String? ?? '',
        distanceM: json['distance_m'] as int? ?? 0,
        start: LatLng(
          (json['start_lat'] as num).toDouble(),
          (json['start_lng'] as num).toDouble(),
        ),
        finish: LatLng(
          (json['finish_lat'] as num).toDouble(),
          (json['finish_lng'] as num).toDouble(),
        ),
        createdBy: json['created_by'] as int?,
        createdAt: json['created_at'] != null
            ? DateTime.parse(json['created_at'] as String)
            : null,
      );
}

/// GET /crews/{id}/sessions items 요소 (session-api.md §2).
class SessionSummary {
  const SessionSummary({
    required this.id,
    required this.crewId,
    required this.courseId,
    required this.courseName,
    required this.status,
    required this.scheduledAt,
    required this.uploadDeadline,
    required this.participantCount,
  });

  final int id;
  final int crewId;
  final int courseId;
  final String courseName;
  final RaceStatus status;
  final DateTime scheduledAt;
  final DateTime uploadDeadline;
  final int participantCount;

  factory SessionSummary.fromJson(Map<String, dynamic> json) => SessionSummary(
        id: json['id'] as int,
        crewId: json['crew_id'] as int,
        courseId: json['course_id'] as int,
        courseName: json['course_name'] as String? ?? '',
        status: RaceStatus.parse(json['status'] as String?),
        scheduledAt: DateTime.parse(json['scheduled_at'] as String),
        uploadDeadline: DateTime.parse(json['upload_deadline'] as String),
        participantCount: json['participant_count'] as int? ?? 0,
      );
}

/// SessionDetail.participants 요소 (session-api.md §3). 탈퇴 유저는 서버가
/// nickname 을 익명 표시("탈퇴한 러너")하되 행 보존(user_id 유지).
class ParticipantView {
  const ParticipantView({
    required this.userId,
    required this.nickname,
    required this.status,
  });

  final int userId;
  final String nickname;
  final ParticipationStatus status;

  factory ParticipantView.fromJson(Map<String, dynamic> json) =>
      ParticipantView(
        userId: json['user_id'] as int,
        nickname: json['nickname'] as String,
        status: ParticipationStatus.parse(json['status'] as String?),
      );
}

/// GET /sessions/{id} (session-api.md §3). 생성/open/register/start/cancel 응답도
/// 동일 shape — 상태만 다름.
class SessionDetail {
  const SessionDetail({
    required this.id,
    required this.crewId,
    required this.course,
    required this.status,
    required this.scheduledAt,
    required this.uploadDeadline,
    required this.participants,
  });

  final int id;
  final int crewId;
  final CourseDetail course;
  final RaceStatus status;
  final DateTime scheduledAt;
  final DateTime uploadDeadline;
  final List<ParticipantView> participants;

  /// STARTED 참가자 존재 = 화면 "지금 뛰는 중" 배지 근거(B2-C1 ③).
  bool get hasRunners => participants.any((p) => p.status.isRunningNow);

  factory SessionDetail.fromJson(Map<String, dynamic> json) => SessionDetail(
        id: json['id'] as int,
        crewId: json['crew_id'] as int,
        course:
            CourseDetail.fromJson(json['course'] as Map<String, dynamic>),
        status: RaceStatus.parse(json['status'] as String?),
        scheduledAt: DateTime.parse(json['scheduled_at'] as String),
        uploadDeadline: DateTime.parse(json['upload_deadline'] as String),
        participants: [
          for (final p in (json['participants'] as List<dynamic>? ?? const []))
            ParticipantView.fromJson(p as Map<String, dynamic>),
        ],
      );
}

/// 세션 생성 요청 (session-api.md §1). course_id + 시각 2종.
/// upload_deadline 기본값 "+12h" 는 **앱레이어 UX**(도메인 하드코딩 금지) —
/// [defaultUploadDeadline] 참조.
class CreateSessionRequest {
  const CreateSessionRequest({
    required this.courseId,
    required this.scheduledAt,
    required this.uploadDeadline,
  });

  final int courseId;
  final DateTime scheduledAt;
  final DateTime uploadDeadline;

  Map<String, dynamic> toJson() => {
        'course_id': courseId,
        // 계약: UTC. toUtc 로 보정 후 ISO8601.
        'scheduled_at': scheduledAt.toUtc().toIso8601String(),
        'upload_deadline': uploadDeadline.toUtc().toIso8601String(),
      };
}

/// upload_deadline UX 기본값 = scheduled_at + 12h (앱레이어 — session-api.md §1).
DateTime defaultUploadDeadline(DateTime scheduledAt) =>
    scheduledAt.add(const Duration(hours: 12));
