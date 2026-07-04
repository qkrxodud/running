import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/race/race_state_machine.dart';

/// 클라 로컬 상태머신 골든 — READY→RUNNING→FINISHED_LOCAL→UPLOADED.
/// 서버 Participation 과 별개(FINISHED_LOCAL 은 서버 미전송 상태).
void main() {
  const s = RaceLocalSession(sessionId: 91);

  group('정상 전이 경로', () {
    test('READY→RUNNING→FINISHED_LOCAL→UPLOADED 순차 진행', () {
      final running = s.start();
      expect(running.state, RaceLocalState.running);
      expect(running.isRunning, isTrue);

      final finished = running.finishLocal();
      expect(finished.state, RaceLocalState.finishedLocal);
      expect(finished.isAwaitingUpload, isTrue);

      final uploaded = finished.markUploaded();
      expect(uploaded.state, RaceLocalState.uploaded);
      expect(uploaded.isUploaded, isTrue);
    });

    test('전이는 sessionId 를 보존한다', () {
      expect(s.start().finishLocal().markUploaded().sessionId, 91);
    });
  });

  group('불변식: FINISHED_LOCAL 은 서버 미전송 — 업로드 성공 확인 후에만 UPLOADED', () {
    test('RUNNING 에서 UPLOADED 로 건너뛸 수 없다(완주 로컬 확정 선행 필수)', () {
      expect(() => s.start().markUploaded(),
          throwsA(isA<RaceStateTransitionError>()));
    });

    test('READY 에서 바로 markUploaded 불가', () {
      expect(() => s.markUploaded(),
          throwsA(isA<RaceStateTransitionError>()));
    });

    test('완주 대기(FINISHED_LOCAL)는 로컬 데이터 보존 대상', () {
      expect(s.start().finishLocal().retainsLocalData, isTrue);
      expect(s.start().retainsLocalData, isTrue);
      expect(s.start().finishLocal().markUploaded().retainsLocalData, isFalse);
    });
  });

  group('동일 세션 중복 시작 방지', () {
    test('이미 RUNNING 인 세션은 재시작 불가', () {
      expect(() => s.start().start(),
          throwsA(isA<RaceStateTransitionError>()));
    });

    test('FINISHED_LOCAL·UPLOADED 에서도 start 불가', () {
      final finished = s.start().finishLocal();
      expect(() => finished.start(), throwsA(isA<RaceStateTransitionError>()));
      expect(() => finished.markUploaded().start(),
          throwsA(isA<RaceStateTransitionError>()));
    });

    test('finishLocal 은 RUNNING 에서만', () {
      expect(() => s.finishLocal(), throwsA(isA<RaceStateTransitionError>()));
    });
  });

  group('단일 활성 레이스 불변식 (canStartNewRace)', () {
    test('진행 중이 없으면(null) 새 레이스 허용', () {
      expect(canStartNewRace(null), isTrue);
    });

    test('RUNNING·FINISHED_LOCAL 이 있으면 새 레이스 거부', () {
      expect(canStartNewRace(s.start()), isFalse);
      expect(canStartNewRace(s.start().finishLocal()), isFalse);
    });

    test('UPLOADED 로 마무리됐으면 새 레이스 허용', () {
      expect(canStartNewRace(s.start().finishLocal().markUploaded()), isTrue);
    });
  });

  group('영속(리커버리) 직렬화', () {
    test('toJson/fromJson 왕복 — 강제종료 복구용', () {
      final finished = s.start().finishLocal();
      final restored = RaceLocalSession.fromJson(finished.toJson());
      expect(restored, finished);
      expect(restored.state, RaceLocalState.finishedLocal);
    });

    test('wire 값은 로컬 상태 문자열(서버 계약 아님)', () {
      expect(RaceLocalState.finishedLocal.wire, 'FINISHED_LOCAL');
      expect(RaceLocalState.fromWire('UPLOADED'), RaceLocalState.uploaded);
      expect(() => RaceLocalState.fromWire('STARTED'), throwsArgumentError);
    });
  });
}
