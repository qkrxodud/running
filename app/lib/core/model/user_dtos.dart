/// user-api.md v0.1 계약 DTO — 값 집합의 진실은 계약 문서.
/// 모든 enum 은 unknown 폴백(R-001 방지, 설계 12 §6).
library;

import 'enum_codec.dart';

/// `user.status`: {ACTIVE, WITHDRAWN} (user-api.md)
enum UserStatus {
  active('ACTIVE'),
  withdrawn('WITHDRAWN'),
  unknown(null);

  const UserStatus(this.wire);
  final String? wire;

  /// 계약 값 집합 (대조 테스트 대상).
  static Map<String, UserStatus> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static UserStatus parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: UserStatus.unknown,
        context: 'user.status',
      );
}

/// `platform`: {ANDROID, IOS} (user-api.md·app-version.md 동일 집합)
enum AppPlatform {
  android('ANDROID'),
  ios('IOS'),
  unknown(null);

  const AppPlatform(this.wire);
  final String? wire;

  static Map<String, AppPlatform> get wireValues => {
        for (final v in values)
          if (v.wire != null) v.wire!: v,
      };

  static AppPlatform parse(String? raw) => parseContractEnum(
        wire: raw,
        values: wireValues,
        unknown: AppPlatform.unknown,
        context: 'platform',
      );

  /// 송신용 — unknown 직렬화 금지(설계 12 §6.4).
  String toWire() {
    final w = wire;
    if (w == null) throw ContractEnumSendError('platform');
    return w;
  }
}

/// GET /users/me 응답 (user-api.md §1). PUT nickname 응답도 동일 shape.
class UserProfile {
  const UserProfile({
    required this.id,
    required this.nickname,
    required this.status,
    required this.onboardingCompleted,
    required this.createdAt,
  });

  final int id;
  final String nickname;
  final UserStatus status;
  final bool onboardingCompleted;
  final DateTime createdAt;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        id: json['id'] as int,
        nickname: json['nickname'] as String,
        status: UserStatus.parse(json['status'] as String?),
        onboardingCompleted: json['onboarding_completed'] as bool? ?? false,
        createdAt: DateTime.parse(json['created_at'] as String),
      );
}
