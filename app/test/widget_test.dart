import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:running/spike/spike_screen.dart';

void main() {
  testWidgets('스파이크 화면이 시작 버튼과 대기 상태를 표시한다', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: SpikeScreen()));
    expect(find.text('기록 시작'), findsOneWidget);
    expect(find.text('대기'), findsOneWidget);
  });
}
