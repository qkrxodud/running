import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/auth_dtos.dart';
import 'package:running/data/api_client.dart';
import 'package:running/data/token_store.dart';

/// AuthInterceptor 401 규약 (auth-api.md §3) — 무한 갱신 루프 금지 검증.
void main() {
  const protected = '/api/v1/users/me';

  /// dio + retryClient 를 같은 프로그래머블 어댑터로 묶어 인터셉터만 격리 검증.
  ({Dio dio, InMemoryTokenStore store, List<String> refreshLog, List<bool> expired})
      build({
    required ResponseBody Function(RequestOptions o) responder,
    required Future<TokenPair?> Function(String rt) refresher,
  }) {
    final adapter = _ProgrammableAdapter(responder);
    final opts = BaseOptions(baseUrl: 'http://test');
    final retry = Dio(opts)..httpClientAdapter = adapter;
    final dio = Dio(opts)..httpClientAdapter = adapter;
    final store = InMemoryTokenStore();
    store.save(const TokenPair(accessToken: 'old-a', refreshToken: 'old-r'));
    final refreshLog = <String>[];
    final expired = <bool>[];
    dio.interceptors.add(AuthInterceptor(
      tokenStore: store,
      refresher: (rt) async {
        refreshLog.add(rt);
        return refresher(rt);
      },
      retryClient: retry,
      onSessionExpired: () => expired.add(true),
    ));
    return (dio: dio, store: store, refreshLog: refreshLog, expired: expired);
  }

  ResponseBody jsonBody(Map<String, dynamic> body, int status) =>
      ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: {
          Headers.contentTypeHeader: ['application/json'],
        },
      );

  test('AUTH_TOKEN_EXPIRED → refresh 1회 후 원요청 재시도 성공', () async {
    final h = build(
      responder: (o) {
        if (o.extra['auth_retried'] == true) {
          return jsonBody({'id': 3, 'ok': true}, 200);
        }
        return jsonBody({'code': 'AUTH_TOKEN_EXPIRED', 'message': '만료'}, 401);
      },
      refresher: (_) async =>
          const TokenPair(accessToken: 'new-a', refreshToken: 'new-r'),
    );

    final res = await h.dio.get<Map<String, dynamic>>(protected);

    expect(res.statusCode, 200);
    expect(res.data!['ok'], true);
    expect(h.refreshLog, ['old-r'], reason: 'refresh 는 정확히 1회');
    expect((await h.store.read())!.accessToken, 'new-a',
        reason: '회전된 새 쌍이 저장됨');
    expect(h.expired, isEmpty, reason: '성공 시 세션 만료 없음');
  });

  test('UNAUTHORIZED → 갱신 시도 없이 즉시 세션 만료', () async {
    final h = build(
      responder: (o) => jsonBody({'code': 'UNAUTHORIZED', 'message': '위조'}, 401),
      refresher: (_) async =>
          const TokenPair(accessToken: 'new-a', refreshToken: 'new-r'),
    );

    await expectLater(
      h.dio.get<Map<String, dynamic>>(protected),
      throwsA(isA<DioException>()),
    );
    expect(h.refreshLog, isEmpty, reason: 'UNAUTHORIZED 는 갱신하지 않음');
    expect(h.expired, [true]);
    expect(await h.store.read(), isNull, reason: '토큰 폐기됨');
  });

  test('무한 루프 금지: refresh 성공했으나 재시도도 401 → refresh 는 여전히 1회', () async {
    final h = build(
      // 재시도 여부와 무관하게 항상 만료 응답 (서버가 계속 401).
      responder: (o) =>
          jsonBody({'code': 'AUTH_TOKEN_EXPIRED', 'message': '만료'}, 401),
      refresher: (_) async =>
          const TokenPair(accessToken: 'new-a', refreshToken: 'new-r'),
    );

    await expectLater(
      h.dio.get<Map<String, dynamic>>(protected),
      throwsA(isA<DioException>()),
    );
    expect(h.refreshLog.length, 1, reason: '재시도는 1회만 — 무한 갱신 루프 없음');
    expect(h.expired, [true], reason: '재시도 401 이면 세션 만료 처리');
  });

  test('refresh 실패(null) → 즉시 세션 만료, 재시도 없음', () async {
    final h = build(
      responder: (o) =>
          jsonBody({'code': 'AUTH_TOKEN_EXPIRED', 'message': '만료'}, 401),
      refresher: (_) async => null,
    );

    await expectLater(
      h.dio.get<Map<String, dynamic>>(protected),
      throwsA(isA<DioException>()),
    );
    expect(h.refreshLog.length, 1);
    expect(h.expired, [true]);
  });

  test('인증 불요 경로(auth/login)의 401 은 인터셉터가 건드리지 않는다', () async {
    final h = build(
      responder: (o) =>
          jsonBody({'code': 'AUTH_KAKAO_TOKEN_INVALID', 'message': '거부'}, 401),
      refresher: (_) async =>
          const TokenPair(accessToken: 'x', refreshToken: 'y'),
    );

    await expectLater(
      h.dio.post<Map<String, dynamic>>('/api/v1/auth/login'),
      throwsA(isA<DioException>()),
    );
    expect(h.refreshLog, isEmpty);
    expect(h.expired, isEmpty, reason: '로그인 실패는 세션 만료 아님');
  });
}

/// options 를 받아 캔드 응답을 돌려주는 테스트용 어댑터.
class _ProgrammableAdapter implements HttpClientAdapter {
  _ProgrammableAdapter(this.responder);

  final ResponseBody Function(RequestOptions options) responder;

  @override
  void close({bool force = false}) {}

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async =>
      responder(options);
}
