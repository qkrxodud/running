import 'dart:async';

import 'package:dio/dio.dart';

import '../core/model/api_error.dart';
import '../core/model/auth_dtos.dart';
import 'token_store.dart';

/// API base URL — dart-define 주입 (dev/prod 분리 골격).
/// 예: flutter run --dart-define=API_BASE_URL=http://192.168.0.10:8080
const String apiBaseUrl = String.fromEnvironment(
  'API_BASE_URL',
  defaultValue: 'http://10.0.2.2:8080', // Android 에뮬레이터 → 호스트 localhost
);

/// 인증 불요 경로 (auth-api.md 토큰 개요) — Authorization 미첨부·401 복구 미적용.
const List<String> noAuthPaths = [
  '/api/v1/app-version',
  '/api/v1/auth/',
];

bool isNoAuthPath(String path) =>
    noAuthPaths.any((p) => p.endsWith('/') ? path.startsWith(p) : path == p);

/// DioException → [ApiException] 정규화. 오류 응답은 계약 `{code,message}` 파싱.
ApiException toApiException(DioException e) {
  final response = e.response;
  if (response == null) {
    return ApiException(
      statusCode: 0,
      code: 'NETWORK_ERROR',
      message: e.message ?? '네트워크 오류',
    );
  }
  final data = response.data;
  if (data is Map<String, dynamic> && data['code'] is String) {
    final body = ApiErrorBody.fromJson(data);
    return ApiException(
      statusCode: response.statusCode ?? 0,
      code: body.code,
      message: body.message,
    );
  }
  // 계약 shape 가 아닌 오류(프록시 HTML 등) — 조용히 삼키지 않고 코드로 노출.
  return ApiException(
    statusCode: response.statusCode ?? 0,
    code: 'MALFORMED_ERROR_BODY',
    message: '오류 응답 형식이 계약과 다릅니다',
  );
}

/// refresh 수행 함수 — 인터셉터가 인증 API 에 직접 의존하지 않게 주입.
/// 성공 시 새 쌍, 실패(AUTH_REFRESH_INVALID 등) 시 null.
typedef TokenRefresher = Future<TokenPair?> Function(String refreshToken);

/// 401 복구 인터셉터 (auth-api.md §3 규약 구현).
///
/// | code | 행동 |
/// |---|---|
/// | AUTH_TOKEN_EXPIRED | refresh **1회** → 원요청 재시도. 실패 시 세션 만료 처리 |
/// | UNAUTHORIZED / AUTH_REFRESH_INVALID | 즉시 토큰 폐기 → 재로그인 유도 |
///
/// 재시도는 요청당 1회만(`_retriedFlag`) — 무한 갱신 루프 금지.
/// QueuedInterceptor: 동시 401 들이 refresh 를 중복 수행하지 않게 직렬화.
class AuthInterceptor extends QueuedInterceptor {
  AuthInterceptor({
    required this.tokenStore,
    required this.refresher,
    required this.retryClient,
    this.onSessionExpired,
  });

  final TokenStore tokenStore;
  final TokenRefresher refresher;

  /// 원요청 재시도용 dio (인터셉터 미부착 — 재귀 방지).
  final Dio retryClient;

  /// 세션 만료(재로그인 필요) 통지 — AuthController 가 로그아웃 상태 전이.
  final void Function()? onSessionExpired;

  static const _retriedFlag = 'auth_retried';

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    if (!isNoAuthPath(options.path)) {
      final tokens = await tokenStore.read();
      if (tokens != null) {
        options.headers['Authorization'] = 'Bearer ${tokens.accessToken}';
      }
    }
    handler.next(options);
  }

  @override
  Future<void> onError(
    DioException err,
    ErrorInterceptorHandler handler,
  ) async {
    final response = err.response;
    final options = err.requestOptions;

    if (response?.statusCode != 401 ||
        isNoAuthPath(options.path) ||
        options.extra[_retriedFlag] == true) {
      handler.next(err);
      return;
    }

    final code = _errorCode(response);
    if (code != AuthErrorCodes.tokenExpired) {
      // UNAUTHORIZED(부재·위조·WITHDRAWN) 등 — 갱신 시도 없이 즉시 재로그인.
      await _expireSession();
      handler.next(err);
      return;
    }

    // AUTH_TOKEN_EXPIRED → refresh 1회.
    final current = await tokenStore.read();
    if (current == null) {
      await _expireSession();
      handler.next(err);
      return;
    }
    final renewed = await refresher(current.refreshToken);
    if (renewed == null) {
      await _expireSession();
      handler.next(err);
      return;
    }
    await tokenStore.save(renewed);

    // 원요청 재시도 (1회 플래그 — 재실패 시 그대로 오류 전파).
    try {
      final retryResponse = await retryClient.fetch<dynamic>(
        options
          ..headers['Authorization'] = 'Bearer ${renewed.accessToken}'
          ..extra[_retriedFlag] = true,
      );
      handler.resolve(retryResponse);
    } on DioException catch (retryErr) {
      if (retryErr.response?.statusCode == 401) await _expireSession();
      handler.next(retryErr);
    }
  }

  String? _errorCode(Response<dynamic>? response) {
    final data = response?.data;
    if (data is Map<String, dynamic>) return data['code'] as String?;
    return null;
  }

  Future<void> _expireSession() async {
    await tokenStore.clear();
    onSessionExpired?.call();
  }
}

/// dio 인스턴스 조립 (composition root 에서 1회).
Dio createApiClient({
  required TokenStore tokenStore,
  required TokenRefresher refresher,
  void Function()? onSessionExpired,
  String? baseUrl,
}) {
  final options = BaseOptions(
    baseUrl: baseUrl ?? apiBaseUrl,
    connectTimeout: const Duration(seconds: 5),
    receiveTimeout: const Duration(seconds: 10),
    contentType: 'application/json; charset=utf-8',
  );
  final retryClient = Dio(options);
  final dio = Dio(options);
  dio.interceptors.add(AuthInterceptor(
    tokenStore: tokenStore,
    refresher: refresher,
    retryClient: retryClient,
    onSessionExpired: onSessionExpired,
  ));
  return dio;
}
