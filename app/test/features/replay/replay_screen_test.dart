import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:running/app/providers.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/core/model/replay_dtos.dart';
import 'package:running/features/replay/replay_screen.dart';

import '../../support/fakes.dart';

/// 리플레이 뷰어 위젯 — 상태별 UI(GENERATING/FAILED/READY)·버전 게이트.
/// ReplayScreen 은 Ticker 를 돌리므로 pumpAndSettle 대신 pump() 로 구동한다.
void main() {
  // overrides 는 List<Override> — 타입명 미노출로 dynamic 수신.
  Widget wrap(dynamic overrides) => ProviderScope(
        overrides: overrides,
        child: const MaterialApp(home: ReplayScreen(sessionId: 91)),
      );

  ReplaySnapshotResponse ready({int schemaVersion = 1}) =>
      ReplaySnapshotResponse.fromJson({
        'status': 'READY',
        'schema_version': schemaVersion,
        'display_names': {'7': '지현', '8': '탈퇴한 러너'},
        'payload': {
          'schema_version': schemaVersion,
          'session_id': 91,
          'course': {
            'distance_m': 5000,
            'route_polyline': '_p~iF~ps|U',
            'start': {'lat': 37.5121, 'lng': 127.0018},
            'finish': {'lat': 37.5288, 'lng': 127.0219},
          },
          'duration_ms': 12000,
          'participants': [
            {
              'user_id': 7,
              'finish_status': 'FINISHED',
              'finish_time_ms': 12000,
              'frames': [
                {'t_ms': 0, 'lat': 37.5121, 'lng': 127.0018, 'cum_dist_m': 0, 'is_gap': false},
                {'t_ms': 12000, 'lat': 37.5288, 'lng': 127.0219, 'cum_dist_m': 5000, 'is_gap': false},
              ],
              'segments': [
                {'seg_index': 0, 'start_dist_m': 0, 'end_dist_m': 500, 'pace_s_per_km': 240, 'color_bucket': 0},
              ],
            },
            {
              'user_id': 8,
              'finish_status': 'DNF',
              'finish_time_ms': null,
              'frames': [
                {'t_ms': 0, 'lat': 37.5121, 'lng': 127.0018, 'cum_dist_m': 0, 'is_gap': false},
                {'t_ms': 5000, 'lat': 37.5170, 'lng': 127.0085, 'cum_dist_m': 2000, 'is_gap': false},
              ],
              'segments': [],
            },
          ],
          'overtakes': [],
        },
      });

  testWidgets('GENERATING → "리플레이 만드는 중"', (tester) async {
    await tester.pumpWidget(wrap([
      replayRepositoryProvider.overrideWithValue(FakeReplayRepository(
          response: const ReplaySnapshotResponse(
              status: ReplayStatus.generating,
              schemaVersion: null,
              displayNames: null,
              payload: null))),
    ]));
    await tester.pump(const Duration(milliseconds: 20));

    expect(find.text('리플레이 만드는 중'), findsOneWidget);
    expect(find.text('새로고침'), findsOneWidget);
  });

  testWidgets('FAILED → 재시도 안내', (tester) async {
    await tester.pumpWidget(wrap([
      replayRepositoryProvider.overrideWithValue(FakeReplayRepository(
          response: const ReplaySnapshotResponse(
              status: ReplayStatus.failed,
              schemaVersion: null,
              displayNames: null,
              payload: null))),
    ]));
    await tester.pump(const Duration(milliseconds: 20));

    expect(find.text('리플레이를 만들지 못했어요'), findsOneWidget);
    expect(find.text('다시 시도'), findsOneWidget);
  });

  testWidgets('schema_version > MAX → "앱 업데이트가 필요해요"(크래시 금지)', (tester) async {
    await tester.pumpWidget(wrap([
      replayRepositoryProvider
          .overrideWithValue(FakeReplayRepository(response: ready(schemaVersion: 2))),
    ]));
    await tester.pump(const Duration(milliseconds: 20));

    expect(find.text('앱 업데이트가 필요해요'), findsOneWidget);
    // 뷰어 컨트롤은 렌더되지 않음(렌더 거부).
    expect(find.byIcon(Icons.play_arrow), findsNothing);
  });

  testWidgets('READY → 뷰어(재생 버튼·배속·타임라인·범례) 렌더', (tester) async {
    await tester.pumpWidget(wrap([
      replayRepositoryProvider
          .overrideWithValue(FakeReplayRepository(response: ready())),
    ]));
    await tester.pump(const Duration(milliseconds: 20));

    // 재생 버튼(정지 상태 → play_arrow).
    expect(find.byIcon(Icons.play_arrow), findsOneWidget);
    // 배속 표시(기본 1x).
    expect(find.text('1x'), findsOneWidget);
    // 총 길이 12000ms → 00:12.
    expect(find.text('00:12'), findsOneWidget);
    // 참가자 범례(조인 이름) + DNF 표기.
    expect(find.textContaining('지현'), findsOneWidget);
    expect(find.textContaining('(미완주)'), findsOneWidget); // DNF 참가자
  });

  testWidgets('READY → 재생 토글 시 일시정지 아이콘으로 전환', (tester) async {
    await tester.pumpWidget(wrap([
      replayRepositoryProvider
          .overrideWithValue(FakeReplayRepository(response: ready())),
    ]));
    await tester.pump(const Duration(milliseconds: 20));

    await tester.tap(find.byIcon(Icons.play_arrow));
    await tester.pump(const Duration(milliseconds: 16));

    expect(find.byIcon(Icons.pause), findsOneWidget);
  });

  // 오류 상태 문구는 순수 함수 replayErrorText 로 분리해 단위 테스트한다
  // (Ticker 활성 하 async provider 에러 전파는 위젯 테스트에 부적합 — 순수 로직 분리).
  group('replayErrorText (순수 — 404 vs 그 외)', () {
    test('404(스냅샷 미생성/미준비) → "리플레이가 아직 없어요"', () {
      final t = replayErrorText(const ApiException(
          statusCode: 404, code: 'NOT_FOUND', message: '스냅샷 없음'));
      expect(t.is404, isTrue);
      expect(t.title, '리플레이가 아직 없어요');
    });

    test('네트워크/기타 → "불러오지 못했어요"', () {
      final net = replayErrorText(const ApiException(
          statusCode: 0, code: 'NETWORK_ERROR', message: ''));
      expect(net.is404, isFalse);
      expect(net.title, '리플레이를 불러오지 못했어요');
      expect(replayErrorText(Exception('x')).is404, isFalse);
    });
  });
}
