import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app/app_theme.dart';
import 'app/router.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // 서비스 isolate ↔ UI 통신 포트 초기화 (flutter_foreground_task).
  // 트래커/스파이크의 sendDataToMain 수신 전제 — 앱 부트스트랩(composition root)에서 1회.
  FlutterForegroundTask.initCommunicationPort();
  runApp(const ProviderScope(child: RunningCrewApp()));
}

class RunningCrewApp extends StatelessWidget {
  const RunningCrewApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: '러닝크루',
      theme: AppTheme.light(),
      routerConfig: _router,
    );
  }
}

final _router = createRouter();
