/// crew-api.md v0.2 계약 DTO — enum 은 전부 unknown 폴백(R-001 방지).
library;

import 'enum_codec.dart';

/// `crew.status`: {ACTIVE, CLOSED} (crew-api.md v0.2)
enum CrewStatus {
  active('ACTIVE'),
  closed('CLOSED'),
  unknown(null);

  const CrewStatus(this.wire);
  final String? wire;

  static Map<String, CrewStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static CrewStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: CrewStatus.unknown,
        context: 'crew.status',
      );
}

/// `crew_member.role`: {LEADER, MEMBER} (crew-api.md v0.2)
enum CrewRole {
  leader('LEADER'),
  member('MEMBER'),
  unknown(null);

  const CrewRole(this.wire);
  final String? wire;

  static Map<String, CrewRole> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static CrewRole parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: CrewRole.unknown,
        context: 'crew_member.role',
      );
}

/// `crew_member.status`: {ACTIVE, WITHDRAWN} (crew-api.md v0.2)
enum CrewMemberStatus {
  active('ACTIVE'),
  withdrawn('WITHDRAWN'),
  unknown(null);

  const CrewMemberStatus(this.wire);
  final String? wire;

  static Map<String, CrewMemberStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static CrewMemberStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: CrewMemberStatus.unknown,
        context: 'crew_member.status',
      );
}

/// GET /crews items 요소 (crew-api.md §2).
class CrewSummary {
  const CrewSummary({
    required this.id,
    required this.name,
    required this.status,
    required this.memberCount,
    required this.role,
    required this.createdAt,
  });

  final int id;
  final String name;
  final CrewStatus status;
  final int memberCount;

  /// 요청자의 역할.
  final CrewRole role;
  final DateTime createdAt;

  factory CrewSummary.fromJson(Map<String, dynamic> json) => CrewSummary(
        id: json['id'] as int,
        name: json['name'] as String,
        status: CrewStatus.parse(json['status'] as String?),
        memberCount: json['member_count'] as int? ?? 0,
        role: CrewRole.parse(json['role'] as String?),
        createdAt: DateTime.parse(json['created_at'] as String),
      );
}

/// CrewDetail.leader / members 요소의 유저 표시 (crew-api.md §3).
class CrewMemberView {
  const CrewMemberView({
    required this.userId,
    required this.nickname,
    this.role = CrewRole.unknown,
    this.joinedAt,
  });

  final int userId;
  final String nickname;
  final CrewRole role;
  final DateTime? joinedAt;

  factory CrewMemberView.fromJson(Map<String, dynamic> json) =>
      CrewMemberView(
        userId: json['user_id'] as int,
        nickname: json['nickname'] as String,
        role: json.containsKey('role')
            ? CrewRole.parse(json['role'] as String?)
            : CrewRole.unknown,
        joinedAt: json['joined_at'] != null
            ? DateTime.parse(json['joined_at'] as String)
            : null,
      );
}

/// GET /crews/{id} 응답 (crew-api.md §3 CrewDetail). 생성·참가 응답도 동일 shape.
class CrewDetail {
  const CrewDetail({
    required this.id,
    required this.name,
    required this.status,
    required this.leader,
    required this.createdAt,
    required this.members,
  });

  final int id;
  final String name;
  final CrewStatus status;
  final CrewMemberView leader;
  final DateTime createdAt;

  /// ACTIVE 멤버만, joined_at 오름차순 — 합류 알림 인앱 갈음 지점(O-1).
  final List<CrewMemberView> members;

  factory CrewDetail.fromJson(Map<String, dynamic> json) => CrewDetail(
        id: json['id'] as int,
        name: json['name'] as String,
        status: CrewStatus.parse(json['status'] as String?),
        leader:
            CrewMemberView.fromJson(json['leader'] as Map<String, dynamic>),
        createdAt: DateTime.parse(json['created_at'] as String),
        members: [
          for (final m in (json['members'] as List<dynamic>? ?? const []))
            CrewMemberView.fromJson(m as Map<String, dynamic>),
        ],
      );
}

/// POST /crews/{id}/invite-codes 응답 201 (crew-api.md §4).
class InviteCodeInfo {
  const InviteCodeInfo({
    required this.code,
    required this.crewId,
    required this.expiresAt,
    required this.maxUses,
    required this.usedCount,
  });

  /// 대문자+숫자 6자, 혼동 문자(0/O/1/I) 제외.
  final String code;
  final int crewId;
  final DateTime expiresAt;
  final int maxUses;
  final int usedCount;

  factory InviteCodeInfo.fromJson(Map<String, dynamic> json) =>
      InviteCodeInfo(
        code: json['code'] as String,
        crewId: json['crew_id'] as int,
        expiresAt: DateTime.parse(json['expires_at'] as String),
        maxUses: json['max_uses'] as int,
        usedCount: json['used_count'] as int? ?? 0,
      );
}

/// 초대코드 입력 검증(클라 측 프리체크) — 계약 문자 집합 기준.
///
/// 대문자+숫자 6자, 혼동 문자(0/O/1/I) 제외. 서버가 대문자 정규화하므로
/// 클라는 대문자 변환 후 검증한다.
class InviteCodeFormat {
  const InviteCodeFormat._();

  static const int length = 6;
  static const String allowedChars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

  static bool isValid(String raw) {
    final code = raw.trim().toUpperCase();
    if (code.length != length) return false;
    return code.split('').every(allowedChars.contains);
  }

  static String normalize(String raw) => raw.trim().toUpperCase();
}
