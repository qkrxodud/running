import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';

import 'spike/spike_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // 서비스 isolate ↔ UI 통신 포트 초기화 (flutter_foreground_task)
  FlutterForegroundTask.initCommunicationPort();
  runApp(const RunningCrewApp());
}

class RunningCrewApp extends StatelessWidget {
  const RunningCrewApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '러닝크루',
      theme: ThemeData(
        // 1a 라임 디자인 토큰의 시드만 우선 반영 — 본 화면 작업은 스파이크 통과 후
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFFC7F94E)),
      ),
      home: const SpikeScreen(),
    );
  }
}
