/// replay-api.md v0.1 계약 DTO — 리플레이 스냅샷 스키마 v1(순수 Dart).
///
/// 규범:
/// - **버전 게이트**: `MAX_SUPPORTED_SNAPSHOT_VERSION`(=1). payload.schema_version 이
///   이를 **초과하면 렌더 거부**("앱 업데이트 필요") — 파싱은 하되 크래시 금지(R-001 정신).
/// - **unknown 필드 무시**(append-only 하위호환): Map 접근이라 미지 키는 자동 무시.
/// - enum 미지값 → unknown 폴백(R-001). `finish_status` 는 track_dtos `FinishStatus` 재사용.
/// - 폴리라인/좌표는 core `PolylineCodec`·`LatLng` 재사용(신규 core 의존 없음 — R-002).
/// - **표시명 미내장**(user_id만) — 최상위 `display_names` 조인 맵에서 취득(O-M3-1).
library;

import '../geo/lat_lng.dart';
import 'enum_codec.dart';
import 'track_dtos.dart' show FinishStatus;

/// 뷰어가 렌더 가능한 최대 스냅샷 스키마 버전. 스키마 진화 시 뷰어와 함께 올린다.
const int kMaxSupportedSnapshotVersion = 1;

/// `replay_snapshot.status` — replay-api §Enum. {GENERATING, READY, FAILED}.
enum ReplayStatus {
  generating('GENERATING'),
  ready('READY'),
  failed('FAILED'),
  unknown(null);

  const ReplayStatus(this.wire);
  final String? wire;

  static Map<String, ReplayStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static ReplayStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: ReplayStatus.unknown,
        context: 'replay_snapshot.status',
      );
}

/// GET /sessions/{id}/replay 최상위 응답. status 별 분기.
///
/// GENERATING/FAILED 면 payload·displayNames·schemaVersion 은 null. READY 만 payload.
class ReplaySnapshotResponse {
  const ReplaySnapshotResponse({
    required this.status,
    required this.schemaVersion,
    required this.displayNames,
    required this.payload,
  });

  final ReplayStatus status;

  /// 최상위 편의 노출(뷰어 게이트 조기 판정). READY 만 non-null.
  final int? schemaVersion;

  /// user_id(string) → nickname. READY 만. 탈퇴=`"탈퇴한 러너"`(조회 시점 조인).
  final Map<int, String>? displayNames;

  /// READY 만. 스키마 v1(§2). 미지 상위 버전이어도 파싱은 하되 [isVersionSupported] false.
  final ReplaySnapshot? payload;

  bool get isReady => status == ReplayStatus.ready && payload != null;
  bool get isGenerating => status == ReplayStatus.generating;
  bool get isFailed => status == ReplayStatus.failed;

  /// 뷰어가 렌더 가능한 버전인가. payload 버전 ≤ MAX 여야 렌더(초과=앱 업데이트 필요).
  bool get isVersionSupported =>
      payload != null && payload!.schemaVersion <= kMaxSupportedSnapshotVersion;

  /// 표시명 조회(조인 맵). 없으면 "러너 {id}" 폴백(탈퇴/미조인 안전).
  String displayName(int userId) =>
      displayNames?[userId] ?? '러너 $userId';

  factory ReplaySnapshotResponse.fromJson(Map<String, dynamic> json) {
    final rawNames = json['display_names'] as Map<String, dynamic>?;
    return ReplaySnapshotResponse(
      status: ReplayStatus.parse(json['status'] as String?),
      schemaVersion: json['schema_version'] as int?,
      displayNames: rawNames == null
          ? null
          : {
              for (final e in rawNames.entries)
                if (int.tryParse(e.key) != null)
                  int.parse(e.key): e.value as String,
            },
      // payload 부재/null(GENERATING·FAILED) → null. 미지 필드는 무시(append-only).
      payload: json['payload'] is Map<String, dynamic>
          ? ReplaySnapshot.fromJson(json['payload'] as Map<String, dynamic>)
          : null,
    );
  }
}

/// 스냅샷 payload(스키마 v1).
class ReplaySnapshot {
  const ReplaySnapshot({
    required this.schemaVersion,
    required this.sessionId,
    required this.course,
    required this.durationMs,
    required this.participants,
    required this.overtakes,
  });

  final int schemaVersion;
  final int sessionId;
  final ReplayCourse course;

  /// 슬라이더 길이 — 전 참가자 최대 상대시각.
  final int durationMs;
  final List<ReplayParticipant> participants;
  final List<Overtake> overtakes;

  factory ReplaySnapshot.fromJson(Map<String, dynamic> json) => ReplaySnapshot(
        schemaVersion: json['schema_version'] as int? ?? 1,
        sessionId: json['session_id'] as int? ?? 0,
        course: ReplayCourse.fromJson(
            json['course'] as Map<String, dynamic>? ?? const {}),
        durationMs: json['duration_ms'] as int? ?? 0,
        participants: [
          for (final p
              in (json['participants'] as List<dynamic>? ?? const []))
            ReplayParticipant.fromJson(p as Map<String, dynamic>),
        ],
        overtakes: [
          for (final o in (json['overtakes'] as List<dynamic>? ?? const []))
            Overtake.fromJson(o as Map<String, dynamic>),
        ],
      );
}

class ReplayCourse {
  const ReplayCourse({
    required this.distanceM,
    required this.routePolyline,
    required this.start,
    required this.finish,
  });

  final int distanceM;
  final String routePolyline;
  final LatLng start;
  final LatLng finish;

  factory ReplayCourse.fromJson(Map<String, dynamic> json) => ReplayCourse(
        distanceM: json['distance_m'] as int? ?? 0,
        routePolyline: json['route_polyline'] as String? ?? '',
        start: _latLng(json['start']),
        finish: _latLng(json['finish']),
      );
}

class ReplayParticipant {
  const ReplayParticipant({
    required this.userId,
    required this.finishStatus,
    required this.finishTimeMs,
    required this.frames,
    required this.segments,
  });

  final int userId;
  final FinishStatus finishStatus;

  /// 완주 상대 시각. **DNF 는 null**(frames 는 트랙 끝까지 보존 — 경로 표시).
  final int? finishTimeMs;
  final List<ReplayFrame> frames;
  final List<ReplaySegment> segments;

  bool get isDnf => finishStatus == FinishStatus.dnf;

  factory ReplayParticipant.fromJson(Map<String, dynamic> json) =>
      ReplayParticipant(
        userId: json['user_id'] as int,
        finishStatus: FinishStatus.parse(json['finish_status'] as String?),
        finishTimeMs: json['finish_time_ms'] as int?,
        frames: [
          for (final f in (json['frames'] as List<dynamic>? ?? const []))
            ReplayFrame.fromJson(f as Map<String, dynamic>),
        ],
        segments: [
          for (final s in (json['segments'] as List<dynamic>? ?? const []))
            ReplaySegment.fromJson(s as Map<String, dynamic>),
        ],
      );
}

class ReplayFrame {
  const ReplayFrame({
    required this.tMs,
    required this.lat,
    required this.lng,
    required this.cumDistM,
    required this.isGap,
  });

  /// 상대 경과(t=0 정렬).
  final int tMs;
  final double lat;
  final double lng;
  final int cumDistM;

  /// GPS 유실 보간 구간 — 뷰어가 실측과 다르게(점선 등) 표시.
  final bool isGap;

  LatLng get position => LatLng(lat, lng);

  factory ReplayFrame.fromJson(Map<String, dynamic> json) => ReplayFrame(
        tMs: json['t_ms'] as int? ?? 0,
        lat: (json['lat'] as num).toDouble(),
        lng: (json['lng'] as num).toDouble(),
        cumDistM: json['cum_dist_m'] as int? ?? 0,
        isGap: json['is_gap'] as bool? ?? false,
      );
}

class ReplaySegment {
  const ReplaySegment({
    required this.segIndex,
    required this.startDistM,
    required this.endDistM,
    required this.paceSPerKm,
    required this.colorBucket,
  });

  final int segIndex;
  final int startDistM;
  final int endDistM;
  final int paceSPerKm;

  /// 페이스→색상 버킷 인덱스(뷰어 색상표).
  final int colorBucket;

  factory ReplaySegment.fromJson(Map<String, dynamic> json) => ReplaySegment(
        segIndex: json['seg_index'] as int? ?? 0,
        startDistM: json['start_dist_m'] as int? ?? 0,
        endDistM: json['end_dist_m'] as int? ?? 0,
        paceSPerKm: json['pace_s_per_km'] as int? ?? 0,
        colorBucket: json['color_bucket'] as int? ?? 0,
      );
}

class Overtake {
  const Overtake({
    required this.atDistM,
    required this.passerUserId,
    required this.passedUserId,
    required this.tMs,
  });

  final int atDistM;
  final int passerUserId;
  final int passedUserId;

  /// 추월 발생 상대 시각(뷰어 마킹 위치).
  final int tMs;

  factory Overtake.fromJson(Map<String, dynamic> json) => Overtake(
        atDistM: json['at_dist_m'] as int? ?? 0,
        passerUserId: json['passer_user_id'] as int,
        passedUserId: json['passed_user_id'] as int,
        tMs: json['t_ms'] as int? ?? 0,
      );
}

LatLng _latLng(Object? raw) {
  if (raw is Map<String, dynamic>) {
    return LatLng(
      (raw['lat'] as num?)?.toDouble() ?? 0,
      (raw['lng'] as num?)?.toDouble() ?? 0,
    );
  }
  return const LatLng(0, 0);
}
