import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/model/user_dtos.dart';
import '../core/version/force_update_policy.dart';
import '../data/api_client.dart';
import '../data/app_version_repository.dart';
import '../data/auth_repository.dart';
import '../data/crew_repository.dart';
import '../data/token_store.dart';
import '../data/user_repository.dart';
import 'auth_controller.dart';

/// composition root — 테스트는 이 프로바이더들을 override 해 페이크 주입.

final tokenStoreProvider = Provider<TokenStore>((ref) => SecureTokenStore());

final dioProvider = Provider<Dio>((ref) {
  final tokenStore = ref.watch(tokenStoreProvider);
  // refresh 는 인터셉터 미부착 bare dio 로 (재귀 방지) — AuthRepository 재사용.
  final bareDio = Dio(BaseOptions(baseUrl: apiBaseUrl));
  final bareAuth = HttpAuthRepository(dio: bareDio, tokenStore: tokenStore);
  return createApiClient(
    tokenStore: tokenStore,
    refresher: bareAuth.refresh,
    onSessionExpired: () =>
        ref.read(authControllerProvider.notifier).onSessionExpired(),
  );
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  // 로그인·갱신은 인증 불요 경로 — 인터셉터 부착 dio 여도 무해(noAuthPaths 제외).
  return HttpAuthRepository(
    dio: ref.watch(dioProvider),
    tokenStore: ref.watch(tokenStoreProvider),
  );
});

final userRepositoryProvider = Provider<UserRepository>(
  (ref) => HttpUserRepository(dio: ref.watch(dioProvider)),
);

final crewRepositoryProvider = Provider<CrewRepository>(
  (ref) => HttpCrewRepository(dio: ref.watch(dioProvider)),
);

final appVersionRepositoryProvider = Provider<AppVersionRepository>(
  (ref) => HttpAppVersionRepository(dio: ref.watch(dioProvider)),
);

final authControllerProvider =
    NotifierProvider<AuthController, AuthState>(AuthController.new);

/// 강제 업데이트 판단 (B1-C3): 기동 시 1회. 실패는 통과(가용성 우선).
final forceUpdateRequiredProvider = FutureProvider<bool>((ref) async {
  final info = await ref
      .watch(appVersionRepositoryProvider)
      .fetch(AppPlatform.android);
  if (info == null) return false;
  return ForceUpdatePolicy.isUpdateRequired(
    currentVersion: appVersion,
    minVersion: info.minVersion,
  );
});
