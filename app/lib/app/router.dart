import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../features/crew/crew_create_screen.dart';
import '../features/crew/crew_detail_screen.dart';
import '../features/crew/crew_home_screen.dart';
import '../features/crew/crew_join_screen.dart';
import '../features/history/history_screen.dart';
import '../features/onboarding/onboarding_screen.dart';
import '../features/auth/login_screen.dart';
import '../features/race/result_screen.dart';
import '../features/race/session_create_screen.dart';
import '../features/race/session_detail_screen.dart';
import '../features/race/session_list_screen.dart';
import '../features/settings/settings_screen.dart';
import '../features/splash/splash_screen.dart';
import '../features/update/force_update_screen.dart';
import '../spike/spike_screen.dart';
import 'app_config.dart';
import 'auth_controller.dart';
import 'providers.dart';

/// 앱 라우터 — 인증 상태머신(AuthStatus)·강제 업데이트 게이트에 반응.
///
/// redirect 규칙:
/// - `/spike` 는 개발 도구 — 게이트 우회(실기기 트래킹 검증 보존).
/// - 강제 업데이트 필요 → `/update` 로 고정(차단).
/// - unknown(부트스트랩 중) → `/splash`, loggedOut → `/login`,
///   needsOnboarding → `/onboarding`, authenticated → 앱 라우트.
final routerProvider = Provider<GoRouter>((ref) {
  // 리버포드 상태 변화를 go_router refreshListenable 로 브리지.
  final refresh = ValueNotifier<int>(0);
  ref.listen(authControllerProvider, (_, _) => refresh.value++);
  ref.listen(forceUpdateRequiredProvider, (_, _) => refresh.value++);
  ref.onDispose(refresh.dispose);

  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: refresh,
    redirect: (context, state) => _redirect(state, ref),
    routes: [
      GoRoute(path: '/splash', builder: (c, s) => const SplashScreen()),
      GoRoute(path: '/update', builder: (c, s) => const ForceUpdateScreen()),
      GoRoute(path: '/login', builder: (c, s) => const LoginScreen()),
      GoRoute(path: '/onboarding', builder: (c, s) => const OnboardingScreen()),
      GoRoute(path: '/', builder: (c, s) => const CrewHomeScreen()),
      GoRoute(path: '/settings', builder: (c, s) => const SettingsScreen()),
      GoRoute(path: '/history', builder: (c, s) => const HistoryScreen()),
      // 정적 경로를 :crewId 앞에 선언 (순서 매칭 — 'create'/'join' 우선).
      GoRoute(
        path: '/crews/create',
        builder: (c, s) => const CrewCreateScreen(),
      ),
      GoRoute(path: '/crews/join', builder: (c, s) => const CrewJoinScreen()),
      // 세션 정적 경로를 :crewId 앞 순서로(create 우선 매칭).
      GoRoute(
        path: '/crews/:crewId/sessions/create',
        builder: (c, s) =>
            SessionCreateScreen(crewId: _crewId(s.pathParameters)),
      ),
      GoRoute(
        path: '/crews/:crewId/sessions',
        builder: (c, s) => SessionListScreen(crewId: _crewId(s.pathParameters)),
      ),
      GoRoute(
        path: '/crews/:crewId',
        builder: (c, s) => CrewDetailScreen(crewId: _crewId(s.pathParameters)),
      ),
      // 결과 화면을 상세보다 먼저 선언(정적 세그먼트 'result' 우선 매칭).
      GoRoute(
        path: '/sessions/:sessionId/result',
        builder: (c, s) {
          final id = int.tryParse(s.pathParameters['sessionId'] ?? '') ?? -1;
          return SessionResultScreen(sessionId: id);
        },
      ),
      GoRoute(
        path: '/sessions/:sessionId',
        builder: (c, s) {
          final id = int.tryParse(s.pathParameters['sessionId'] ?? '') ?? -1;
          return SessionDetailScreen(sessionId: id);
        },
      ),
      GoRoute(path: '/spike', builder: (c, s) => const SpikeScreen()),
    ],
  );
});

int _crewId(Map<String, String> params) =>
    int.tryParse(params['crewId'] ?? '') ?? -1;

String? _redirect(GoRouterState state, Ref ref) {
  final loc = state.matchedLocation;
  // /spike 는 dev 도구 — prod 빌드에서는 접근 차단(B2-C4 격리), dev 는 게이트 우회.
  if (loc == '/spike') return AppConfig.showDevTools ? null : '/';

  final forceUpdate =
      ref.read(forceUpdateRequiredProvider).asData?.value ?? false;
  if (forceUpdate) return loc == '/update' ? null : '/update';
  if (loc == '/update') return '/';

  switch (ref.read(authControllerProvider).status) {
    case AuthStatus.unknown:
      return loc == '/splash' ? null : '/splash';
    case AuthStatus.loggedOut:
      return loc == '/login' ? null : '/login';
    case AuthStatus.needsOnboarding:
      return loc == '/onboarding' ? null : '/onboarding';
    case AuthStatus.authenticated:
      if (loc == '/splash' || loc == '/login' || loc == '/onboarding') {
        return '/';
      }
      return null;
  }
}
