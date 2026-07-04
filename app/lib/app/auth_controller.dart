import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/model/user_dtos.dart';
import '../data/auth_repository.dart';
import '../data/token_store.dart';
import '../data/user_repository.dart';
import 'providers.dart';

/// 클라이언트 인증 상태 (라우터 리다이렉트의 기준).
enum AuthStatus {
  /// 부트스트랩 전 (토큰 확인 중).
  unknown,

  /// 미로그인 — 로그인 화면으로.
  loggedOut,

  /// 로그인됨·온보딩 미완 — 닉네임 설정으로 (onboarding_completed 기준).
  needsOnboarding,

  /// 로그인·온보딩 완료 — 홈 진입 가능.
  authenticated,
}

class AuthState {
  const AuthState({required this.status, this.profile});

  final AuthStatus status;
  final UserProfile? profile;
}

/// 인증 상태머신. 401 세션 만료(인터셉터 통지)·로그인·온보딩·탈퇴 전이 담당.
/// 의존은 프로바이더 경유 — 테스트는 repository 프로바이더 override 로 페이크 주입.
class AuthController extends Notifier<AuthState> {
  TokenStore get _tokenStore => ref.read(tokenStoreProvider);
  AuthRepository get _auth => ref.read(authRepositoryProvider);
  UserRepository get _users => ref.read(userRepositoryProvider);

  @override
  AuthState build() => const AuthState(status: AuthStatus.unknown);

  /// 앱 기동 시 1회: 저장된 토큰으로 프로필 복원.
  Future<void> bootstrap() async {
    final tokens = await _tokenStore.read();
    if (tokens == null) {
      state = const AuthState(status: AuthStatus.loggedOut);
      return;
    }
    try {
      final profile = await _users.me();
      state = AuthState(status: _statusFor(profile), profile: profile);
    } on Object {
      // 401 은 인터셉터가 토큰 폐기 후 실패시킴. 네트워크 오류도 로그인 화면이 안전.
      state = const AuthState(status: AuthStatus.loggedOut);
    }
  }

  /// 스텁 로그인: `stub:{fake_kakao_id}` (auth-api.md §4).
  /// 카카오 SDK 로그인은 M0 앱 키 확보 후 이 메서드 옆에 추가 배선(화면 교체 지점).
  Future<void> loginWithStub(String fakeKakaoId) async {
    await _auth.login('stub:$fakeKakaoId');
    // 토큰은 repository 가 저장. 온보딩 게이트는 서버 컬럼이 진실 — me() 로 확정.
    final profile = await _users.me();
    state = AuthState(status: _statusFor(profile), profile: profile);
  }

  /// 온보딩 최초 설정·이후 수정 공용 (PUT nickname).
  Future<void> setNickname(String nickname) async {
    final profile = await _users.setNickname(nickname);
    state = AuthState(status: _statusFor(profile), profile: profile);
  }

  Future<void> logout() async {
    await _auth.logout();
    state = const AuthState(status: AuthStatus.loggedOut);
  }

  /// 회원 탈퇴 — 성공 시 서버가 토큰 무효화, 클라도 즉시 폐기.
  Future<void> withdraw() async {
    await _users.withdraw();
    await _auth.logout();
    state = const AuthState(status: AuthStatus.loggedOut);
  }

  /// 인터셉터 세션 만료 통지 (토큰은 인터셉터가 이미 폐기).
  void onSessionExpired() {
    state = const AuthState(status: AuthStatus.loggedOut);
  }

  AuthStatus _statusFor(UserProfile profile) => profile.onboardingCompleted
      ? AuthStatus.authenticated
      : AuthStatus.needsOnboarding;
}
