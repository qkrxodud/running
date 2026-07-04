import 'package:go_router/go_router.dart';

import '../features/home/home_screen.dart';
import '../spike/spike_screen.dart';

/// 앱 라우터(go_router). 화면 뼈대(crew/course/race/replay/ranking)는 배치 B에서
/// 이 트리에 추가된다. 현재는 홈 셸 + 스파이크(실기기 검증 대기) 진입점만 둔다.
///
/// 스파이크는 `/spike` 로 보존한다 — 실기기 백그라운드 트래킹 검증이 아직
/// 진행 중이므로 동작을 깨뜨리지 않는다.
GoRouter createRouter() {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/spike',
        builder: (context, state) => const SpikeScreen(),
      ),
    ],
  );
}
