import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app_theme.dart';
import 'app/providers.dart';
import 'app/router.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // 서비스 isolate ↔ UI 통신 포트 초기화 (flutter_foreground_task).
  // 트래커/스파이크의 sendDataToMain 수신 전제 — composition root 에서 1회.
  FlutterForegroundTask.initCommunicationPort();
  runApp(const ProviderScope(child: RunningCrewApp()));
}

class RunningCrewApp extends ConsumerStatefulWidget {
  const RunningCrewApp({super.key});

  @override
  ConsumerState<RunningCrewApp> createState() => _RunningCrewAppState();
}

class _RunningCrewAppState extends ConsumerState<RunningCrewApp> {
  @override
  void initState() {
    super.initState();
    // 기동 시 1회: 저장 토큰으로 인증 복원 + 강제 업데이트 판단 예열.
    // (강제 업데이트 provider 는 router 의 ref.listen 이 이미 활성화한다.)
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(authControllerProvider.notifier).bootstrap();
    });
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(routerProvider);
    return MaterialApp.router(
      title: '러닝크루',
      theme: AppTheme.light(),
      routerConfig: router,
    );
  }
}
