import 'package:dio/dio.dart';

import '../core/model/auth_dtos.dart';
import 'api_client.dart';
import 'token_store.dart';

/// 인증 API (auth-api.md). UI/컨트롤러는 이 추상에만 의존 — 테스트는 페이크.
abstract interface class AuthRepository {
  /// POST /auth/login — 카카오(스텁: `stub:{fake_kakao_id}`) 토큰 → JWT 쌍.
  /// 성공 시 토큰 저장까지 수행.
  Future<LoginResponse> login(String kakaoAccessToken);

  /// POST /auth/refresh — 쌍 회전. 실패 시 null (호출자가 재로그인 유도).
  Future<TokenPair?> refresh(String refreshToken);

  /// 로컬 토큰 폐기 (서버측 폐기는 무상태라 없음 — auth-api.md §2 주의).
  Future<void> logout();
}

class HttpAuthRepository implements AuthRepository {
  HttpAuthRepository({required Dio dio, required TokenStore tokenStore})
      : _dio = dio,
        _tokenStore = tokenStore;

  final Dio _dio;
  final TokenStore _tokenStore;

  @override
  Future<LoginResponse> login(String kakaoAccessToken) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/v1/auth/login',
        data: {
          'kakao_access_token': kakaoAccessToken,
          'client_meta': {'app_version': appVersion},
        },
      );
      final parsed = LoginResponse.fromJson(response.data!);
      await _tokenStore.save(parsed.tokens);
      return parsed;
    } on DioException catch (e) {
      throw toApiException(e);
    }
  }

  @override
  Future<TokenPair?> refresh(String refreshToken) async {
    try {
      final response = await _dio.post<Map<String, dynamic>>(
        '/api/v1/auth/refresh',
        data: {'refresh_token': refreshToken},
      );
      return RefreshResponse.fromJson(response.data!).tokens;
    } on DioException {
      // AUTH_REFRESH_INVALID 포함 전부 — 호출자(인터셉터/컨트롤러)가 재로그인 처리.
      return null;
    }
  }

  @override
  Future<void> logout() => _tokenStore.clear();
}

/// 클라 현재 버전 — pubspec version 과 동기 유지(강제 업데이트 판단·client_meta).
/// TODO(M0 이후): package_info_plus 로 빌드 산출물에서 직접 읽기.
const String appVersion = '1.0.0';
