/// app-version.md v0.1 계약 DTO.
library;

import 'user_dtos.dart' show AppPlatform;

/// GET /app-version 응답 200.
class AppVersionInfo {
  const AppVersionInfo({
    required this.platform,
    required this.minVersion,
    required this.updatedAt,
  });

  final AppPlatform platform;

  /// semver(`major.minor.patch`). 클라 현재 버전 < 이 값이면 강제 업데이트.
  final String minVersion;
  final DateTime updatedAt;

  factory AppVersionInfo.fromJson(Map<String, dynamic> json) =>
      AppVersionInfo(
        platform: AppPlatform.parse(json['platform'] as String?),
        minVersion: json['min_version'] as String,
        updatedAt: DateTime.parse(json['updated_at'] as String),
      );
}
