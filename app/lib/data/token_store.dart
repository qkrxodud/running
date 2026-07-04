import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../core/model/auth_dtos.dart';

/// JWT 쌍 저장 추상 — 테스트는 [InMemoryTokenStore], 실기기는 [SecureTokenStore].
abstract interface class TokenStore {
  Future<TokenPair?> read();

  /// 쌍 회전 규약(auth-api.md §2): 갱신 응답 수신 즉시 구 쌍을 덮어쓴다.
  Future<void> save(TokenPair tokens);

  Future<void> clear();
}

/// flutter_secure_storage 기반 구현 (Android Keystore 암호화).
class SecureTokenStore implements TokenStore {
  SecureTokenStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _accessKey = 'auth.access_token';
  static const _refreshKey = 'auth.refresh_token';

  @override
  Future<TokenPair?> read() async {
    final access = await _storage.read(key: _accessKey);
    final refresh = await _storage.read(key: _refreshKey);
    if (access == null || refresh == null) return null;
    return TokenPair(accessToken: access, refreshToken: refresh);
  }

  @override
  Future<void> save(TokenPair tokens) async {
    await _storage.write(key: _accessKey, value: tokens.accessToken);
    await _storage.write(key: _refreshKey, value: tokens.refreshToken);
  }

  @override
  Future<void> clear() async {
    await _storage.delete(key: _accessKey);
    await _storage.delete(key: _refreshKey);
  }
}

/// 테스트·개발용 인메모리 구현.
class InMemoryTokenStore implements TokenStore {
  TokenPair? _tokens;

  @override
  Future<TokenPair?> read() async => _tokens;

  @override
  Future<void> save(TokenPair tokens) async => _tokens = tokens;

  @override
  Future<void> clear() async => _tokens = null;
}
