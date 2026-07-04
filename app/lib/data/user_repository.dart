import 'package:dio/dio.dart';

import '../core/model/user_dtos.dart';
import 'api_client.dart';

/// user-api.md 소비. 디바이스 토큰 등록은 클라 토큰 취득(Firebase) 대기 — M3.
abstract interface class UserRepository {
  /// GET /users/me
  Future<UserProfile> me();

  /// PUT /users/me/nickname — 온보딩 최초 설정·이후 수정 겸용.
  Future<UserProfile> setNickname(String nickname);

  /// DELETE /users/me — 회원 탈퇴. 성공 시 서버가 토큰 전부 무효화.
  Future<void> withdraw();
}

class HttpUserRepository implements UserRepository {
  HttpUserRepository({required Dio dio}) : _dio = dio;

  final Dio _dio;

  @override
  Future<UserProfile> me() => _guard(() async {
        final r = await _dio.get<Map<String, dynamic>>('/api/v1/users/me');
        return UserProfile.fromJson(r.data!);
      });

  @override
  Future<UserProfile> setNickname(String nickname) => _guard(() async {
        final r = await _dio.put<Map<String, dynamic>>(
          '/api/v1/users/me/nickname',
          data: {'nickname': nickname},
        );
        return UserProfile.fromJson(r.data!);
      });

  @override
  Future<void> withdraw() => _guard(() async {
        await _dio.delete<void>('/api/v1/users/me');
      });

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }
}

/// 닉네임 클라 측 검증 — 계약(user-api.md §2): trim 후 1~30자, 제어문자 금지.
String? validateNickname(String raw) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty) return '닉네임을 입력해 주세요.';
  if (trimmed.length > 30) return '닉네임은 30자 이내여야 합니다.';
  if (trimmed.runes.any((r) => r < 0x20 || r == 0x7f)) {
    return '사용할 수 없는 문자가 포함되어 있습니다.';
  }
  return null;
}
