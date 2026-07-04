import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/crew_dtos.dart';
import 'package:running/core/model/enum_codec.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/core/model/user_dtos.dart';

/// 계약 enum 값 집합 대조 (설계 12 §6.3 — R-001 재발 방지 의무 테스트).
///
/// 값 집합의 진실은 `docs/contracts/`. DTO enum 의 wire 집합이 계약과
/// 벗어나면(서버가 값 추가/이름 변경) 이 테스트가 즉시 깨져 3자 대조를 강제한다.
void main() {
  group('계약 enum 값 집합 == DTO wire 집합', () {
    test('user.status = {ACTIVE, WITHDRAWN} (user-api.md)', () {
      expect(UserStatus.wireValues.keys.toSet(), {'ACTIVE', 'WITHDRAWN'});
    });

    test('platform = {ANDROID, IOS} (user-api.md·app-version.md)', () {
      expect(AppPlatform.wireValues.keys.toSet(), {'ANDROID', 'IOS'});
    });

    test('crew.status = {ACTIVE, CLOSED} (crew-api.md v0.2)', () {
      expect(CrewStatus.wireValues.keys.toSet(), {'ACTIVE', 'CLOSED'});
    });

    test('crew_member.role = {LEADER, MEMBER} (crew-api.md v0.2)', () {
      expect(CrewRole.wireValues.keys.toSet(), {'LEADER', 'MEMBER'});
    });

    test('crew_member.status = {ACTIVE, WITHDRAWN} (crew-api.md v0.2)', () {
      expect(CrewMemberStatus.wireValues.keys.toSet(), {'ACTIVE', 'WITHDRAWN'});
    });

    // Race 컨텍스트 — 미구현 상태값(FINALIZING/COMPLETED/FINISHED/DNF/DNS)도
    // 집합 전량 보존해야 한다(서버가 그 값을 보내도 unknown 아닌 정식 파싱).
    test('race_session.status 6값 전수 (session-api.md v0.2)', () {
      expect(RaceStatus.wireValues.keys.toSet(),
          {'DRAFT', 'OPEN', 'RUNNING', 'FINALIZING', 'COMPLETED', 'CANCELLED'});
    });

    test('participation.status 6값 전수 (session-api.md v0.2)', () {
      expect(ParticipationStatus.wireValues.keys.toSet(), {
        'REGISTERED',
        'STARTED',
        'FINISHED',
        'DNF',
        'DNS',
        'WITHDRAWN',
      });
    });
  });

  group('미지값 폴백 (R-001 방지 — 크래시 금지)', () {
    test('미지 문자열은 unknown 으로 폴백한다', () {
      expect(UserStatus.parse('DELETED'), UserStatus.unknown);
      expect(CrewStatus.parse('ARCHIVED'), CrewStatus.unknown);
      expect(CrewRole.parse('OWNER'), CrewRole.unknown);
      expect(AppPlatform.parse('WEB'), AppPlatform.unknown);
    });

    test('null 도 unknown 으로 폴백한다', () {
      expect(CrewRole.parse(null), CrewRole.unknown);
    });

    test('Race enum 미지값·null 폴백', () {
      expect(RaceStatus.parse('ARCHIVED'), RaceStatus.unknown);
      expect(RaceStatus.parse(null), RaceStatus.unknown);
      expect(ParticipationStatus.parse('LATE'), ParticipationStatus.unknown);
      // 미구현이지만 계약 값은 unknown 아님(정식 파싱).
      expect(ParticipationStatus.parse('DNS'), ParticipationStatus.dns);
      expect(RaceStatus.parse('FINALIZING'), RaceStatus.finalizing);
    });

    test('미지값 수신 시 관측 훅으로 로깅한다', () {
      final observed = <String>[];
      EnumParseLog.onUnknown = (ctx, raw) => observed.add('$ctx=$raw');
      addTearDown(() => EnumParseLog.onUnknown = null);

      CrewStatus.parse('WEIRD');
      expect(observed, contains('crew.status=WEIRD'));
    });
  });

  group('송신 폴백 금지 (설계 12 §6.4)', () {
    test('unknown platform 직렬화는 예외', () {
      expect(() => AppPlatform.unknown.toWire(),
          throwsA(isA<ContractEnumSendError>()));
    });

    test('정상 값은 계약 문자열로 직렬화', () {
      expect(AppPlatform.android.toWire(), 'ANDROID');
      expect(AppPlatform.ios.toWire(), 'IOS');
    });
  });
}
