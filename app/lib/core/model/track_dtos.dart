/// track-api.md **v0.1.1** 계약 DTO — 트랙 업로드/상태/결과 조회(순수 Dart).
///
/// 규범:
/// - 좌표열은 **인코딩 폴리라인 1e5**(course-api 동일 규약) — `PolylineCodec` 재사용,
///   신규 core 의존 만들지 않는다(R-002). 병렬 배열(timestamps/speeds/accuracies/
///   altitudes) 전부 길이 N.
/// - `timestamps` 는 **epoch milliseconds**(conventions §9 대량 배열 예외), GPS 시각 우선.
/// - enum 은 전부 `unknown` 폴백(R-001) + 계약 값집합 대조 테스트.
/// - **키 부재 = null 파싱(P46-1)**: 서버 전역 NON_NULL 직렬화라 DNF/DNS 시
///   `finished_at`/`total_time_s`/`rank`/`record_time_s`/`avg_pace_s_per_km` 등의
///   **키 자체가 생략**된다. `json['x'] as T?` 는 키 부재·명시적 null 을 모두 null 로
///   파싱하므로 그대로 사용한다(존재 강제 금지).
/// - **avg_pace_s_per_km 필드명 고정(P46-2)** — 오타·변형 금지.
library;

import '../geo/lat_lng.dart';
import '../geo/polyline_codec.dart';
import '../tracking/track_point.dart';
import 'enum_codec.dart';

// ─────────────────────────────── enums ───────────────────────────────

/// 업로드 응답 `processing_status` — track-api §1.
/// {PROCESSED} (M2 동기 처리). 비동기 도입 시 RECEIVED/PROCESSING 확장 예약 —
/// 그 값 수신 시 크래시 대신 unknown 폴백.
enum ProcessingStatus {
  processed('PROCESSED'),
  unknown(null);

  const ProcessingStatus(this.wire);
  final String? wire;

  static Map<String, ProcessingStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static ProcessingStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: ProcessingStatus.unknown,
        context: 'track.processing_status',
      );
}

/// 업로드 응답 `finish_status` — track-api §1. {FINISHED, DNF}.
/// DNS 는 미출주(트랙 없음)라 업로드 경로 밖 — 여기엔 없다(결과 조회는 별도 enum).
enum FinishStatus {
  finished('FINISHED'),
  dnf('DNF'),
  unknown(null);

  const FinishStatus(this.wire);
  final String? wire;

  static Map<String, FinishStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static FinishStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: FinishStatus.unknown,
        context: 'track.finish_status',
      );

  bool get isFinished => this == FinishStatus.finished;
}

/// 결과 조회 `entries[].status` — track-api §3. {FINISHED, DNF, DNS}.
/// Participation 최종값의 부분집합(업로드 finish_status + DNS).
enum ResultEntryStatus {
  finished('FINISHED'),
  dnf('DNF'),
  dns('DNS'),
  unknown(null);

  const ResultEntryStatus(this.wire);
  final String? wire;

  static Map<String, ResultEntryStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static ResultEntryStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: ResultEntryStatus.unknown,
        context: 'result.entry.status',
      );

  bool get isFinished => this == ResultEntryStatus.finished;
}

// ─────────────────────────── upload request ───────────────────────────

/// 디버깅용 클라 메타 — `{os, os_version, device_model}` **3키 고정**(conventions §8).
/// 서버 저장·디버깅 전용, 판정 미사용(플랫폼 무지). 3키 외 추가 금지.
class ClientMeta {
  const ClientMeta({
    required this.os,
    required this.osVersion,
    required this.deviceModel,
  });

  final String os;
  final String osVersion;
  final String deviceModel;

  Map<String, dynamic> toJson() => {
        'os': os,
        'os_version': osVersion,
        'device_model': deviceModel,
      };
}

/// POST /api/v1/sessions/{id}/track 요청 (track-api §1).
///
/// 완주 후(FINISHED_LOCAL) 사후 업로드. `client_upload_id` 는 **멱등 키** —
/// 한 번의 완주당 1회 생성하고 재시도 간 **동일 값 재사용**(§4 재업로드 정책).
class TrackUploadRequest {
  const TrackUploadRequest({
    required this.clientUploadId,
    required this.startedAt,
    required this.polyline,
    required this.timestamps,
    required this.speeds,
    required this.accuracies,
    this.altitudes,
    this.clientMeta,
  });

  /// 멱등 키(uuid). 재시도해도 동일 값(§4).
  final String clientUploadId;

  /// 시작 버튼 시각(GPS 시각 우선). 단일 시점이므로 ISO-8601 UTC 로 직렬화.
  final DateTime startedAt;

  /// 1e5 인코딩 폴리라인.
  final String polyline;

  /// GPS 시각(epoch millis, §9 예외). 비내림차순.
  final List<int> timestamps;
  final List<double> speeds;
  final List<double> accuracies;

  /// 선택 — 있으면 길이 N. 고도는 판정 미사용.
  final List<double>? altitudes;
  final ClientMeta? clientMeta;

  /// 트랙 포인트열 → 업로드 요청. 좌표는 `PolylineCodec.encode`(1e5),
  /// timestamps 는 GPS 시각(UTC) epoch millis. [includeAltitude] 로 고도 포함 여부.
  factory TrackUploadRequest.fromPoints({
    required String clientUploadId,
    required DateTime startedAt,
    required List<TrackPoint> points,
    ClientMeta? clientMeta,
    bool includeAltitude = true,
  }) {
    return TrackUploadRequest(
      clientUploadId: clientUploadId,
      startedAt: startedAt,
      polyline:
          PolylineCodec.encode([for (final p in points) LatLng(p.lat, p.lng)]),
      timestamps: [
        for (final p in points) p.timestamp.toUtc().millisecondsSinceEpoch,
      ],
      speeds: [for (final p in points) p.speed],
      accuracies: [for (final p in points) p.accuracy],
      altitudes: includeAltitude
          ? [for (final p in points) p.altitude]
          : null,
      clientMeta: clientMeta,
    );
  }

  Map<String, dynamic> toJson() => {
        'client_upload_id': clientUploadId,
        'started_at': startedAt.toUtc().toIso8601String(),
        'polyline': polyline,
        'timestamps': timestamps,
        'speeds': speeds,
        'accuracies': accuracies,
        if (altitudes != null) 'altitudes': altitudes,
        if (clientMeta != null) 'client_meta': clientMeta!.toJson(),
      };
}

// ─────────────────────────── responses ───────────────────────────

/// 업로드(§1 201/200)·내 트랙 조회(§2 200) 공통 응답 — track_record 요약(블롭 없음).
///
/// **키 부재=null(P46-1)**: DNF 면 `finished_at`/`total_time_s` 키가 생략된다.
class TrackRecordResponse {
  const TrackRecordResponse({
    required this.trackRecordId,
    required this.sessionId,
    required this.userId,
    required this.processingStatus,
    required this.finishStatus,
    required this.startedAt,
    required this.finishedAt,
    required this.totalDistanceM,
    required this.totalTimeS,
    required this.gpsGapCount,
  });

  final int trackRecordId;
  final int sessionId;
  final int userId;
  final ProcessingStatus processingStatus;
  final FinishStatus finishStatus;
  final DateTime startedAt;

  /// 완주 시 도착 최초 진입 시각. **DNF 면 null(키 부재 포함)**.
  final DateTime? finishedAt;

  /// 정제 후 좌표 기반 거리. DNF 도 뛴 만큼 존재(값 없으면 null).
  final int? totalDistanceM;

  /// 그로스 타임. **DNF 면 null(키 부재 포함)**.
  final int? totalTimeS;
  final int gpsGapCount;

  factory TrackRecordResponse.fromJson(Map<String, dynamic> json) =>
      TrackRecordResponse(
        trackRecordId: json['track_record_id'] as int,
        sessionId: json['session_id'] as int,
        userId: json['user_id'] as int,
        processingStatus:
            ProcessingStatus.parse(json['processing_status'] as String?),
        finishStatus: FinishStatus.parse(json['finish_status'] as String?),
        startedAt: DateTime.parse(json['started_at'] as String),
        // P46-1: 키 부재·명시 null 모두 null.
        finishedAt: _parseDateOrNull(json['finished_at']),
        totalDistanceM: json['total_distance_m'] as int?,
        totalTimeS: json['total_time_s'] as int?,
        gpsGapCount: json['gps_gap_count'] as int? ?? 0,
      );
}

/// 결과 응답 course 요약 (§3).
class ResultCourse {
  const ResultCourse({
    required this.id,
    required this.name,
    required this.distanceM,
  });

  final int id;
  final String name;
  final int distanceM;

  factory ResultCourse.fromJson(Map<String, dynamic> json) => ResultCourse(
        id: json['id'] as int,
        name: json['name'] as String? ?? '',
        distanceM: json['distance_m'] as int? ?? 0,
      );
}

/// 결과 순위 항목 (§3 entries[]). **키 부재=null(P46-1)** 전면 적용.
class ResultEntry {
  const ResultEntry({
    required this.userId,
    required this.nickname,
    required this.status,
    required this.rank,
    required this.recordTimeS,
    required this.totalDistanceM,
    required this.avgPaceSPerKm,
    required this.isPb,
  });

  final int userId;
  final String nickname;
  final ResultEntryStatus status;

  /// 완주자만. 동률 공동순위+다음 건너뜀(1,1,3). **DNF/DNS 는 null(키 부재)**.
  final int? rank;
  final int? recordTimeS;

  /// DNS(트랙 없음)는 null, DNF 는 뛴 만큼.
  final int? totalDistanceM;

  /// **필드명 고정: avg_pace_s_per_km(P46-2)**. 완주자만.
  final int? avgPaceSPerKm;
  final bool isPb;

  factory ResultEntry.fromJson(Map<String, dynamic> json) => ResultEntry(
        userId: json['user_id'] as int,
        nickname: json['nickname'] as String? ?? '',
        status: ResultEntryStatus.parse(json['status'] as String?),
        // 아래 5필드 전부 키 부재 가능(P46-1).
        rank: json['rank'] as int?,
        recordTimeS: json['record_time_s'] as int?,
        totalDistanceM: json['total_distance_m'] as int?,
        avgPaceSPerKm: json['avg_pace_s_per_km'] as int?,
        isPb: json['is_pb'] as bool? ?? false,
      );
}

/// GET /api/v1/sessions/{id}/result 200 응답 (§3).
class RaceResultResponse {
  const RaceResultResponse({
    required this.sessionId,
    required this.course,
    required this.finalizedAt,
    required this.entries,
  });

  final int sessionId;
  final ResultCourse course;
  final DateTime finalizedAt;
  final List<ResultEntry> entries;

  factory RaceResultResponse.fromJson(Map<String, dynamic> json) =>
      RaceResultResponse(
        sessionId: json['session_id'] as int,
        course: ResultCourse.fromJson(json['course'] as Map<String, dynamic>),
        finalizedAt: DateTime.parse(json['finalized_at'] as String),
        entries: [
          for (final e in (json['entries'] as List<dynamic>? ?? const []))
            ResultEntry.fromJson(e as Map<String, dynamic>),
        ],
      );
}

DateTime? _parseDateOrNull(Object? raw) =>
    raw is String ? DateTime.parse(raw) : null;
