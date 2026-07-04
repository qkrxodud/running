import 'package:running/core/model/auth_dtos.dart';
import 'package:running/core/model/crew_dtos.dart';
import 'package:running/core/model/page_response.dart';
import 'package:running/core/model/user_dtos.dart';
import 'package:running/data/auth_repository.dart';
import 'package:running/data/crew_repository.dart';
import 'package:running/data/user_repository.dart';

/// 위젯 테스트용 페이크 — 서버 없이 API 계층을 대체한다(B1-C7 완료 기준).

class FakeCrewRepository implements CrewRepository {
  FakeCrewRepository({
    this.crews = const [],
    this.detail_,
    this.invite,
    this.onCreate,
    this.onJoin,
    this.throwOnDetail,
  });

  final List<CrewSummary> crews;
  final CrewDetail? detail_;
  final InviteCodeInfo? invite;
  final CrewDetail Function(String name)? onCreate;
  final CrewDetail Function(String code)? onJoin;
  final Object? throwOnDetail;

  @override
  Future<PageResponse<CrewSummary>> myCrews({int page = 0, int size = 20}) async {
    return PageResponse(
      items: crews,
      page: page,
      size: size,
      totalElements: crews.length,
      totalPages: 1,
    );
  }

  @override
  Future<CrewDetail> create(String name) async =>
      onCreate?.call(name) ?? _stubDetail(1, name);

  @override
  Future<CrewDetail> detail(int crewId) async {
    if (throwOnDetail != null) throw throwOnDetail!;
    return detail_ ?? _stubDetail(crewId, '크루 $crewId');
  }

  @override
  Future<InviteCodeInfo> createInviteCode(
    int crewId, {
    required int maxUses,
    required int expiresInHours,
  }) async =>
      invite ??
      InviteCodeInfo(
        code: 'K7QF2A',
        crewId: crewId,
        expiresAt: DateTime.now().toUtc().add(Duration(hours: expiresInHours)),
        maxUses: maxUses,
        usedCount: 0,
      );

  @override
  Future<CrewDetail> join(String code) async =>
      onJoin?.call(code) ?? _stubDetail(9, '참가한 크루');
}

CrewDetail _stubDetail(int id, String name) => CrewDetail(
      id: id,
      name: name,
      status: CrewStatus.active,
      leader: const CrewMemberView(
        userId: 3,
        nickname: '민수',
        role: CrewRole.leader,
      ),
      createdAt: DateTime.utc(2026, 6, 1),
      members: [
        CrewMemberView(
          userId: 3,
          nickname: '민수',
          role: CrewRole.leader,
          joinedAt: DateTime.utc(2026, 6, 1),
        ),
      ],
    );

class FakeUserRepository implements UserRepository {
  FakeUserRepository({this.profile, this.onSetNickname});

  UserProfile? profile;
  final UserProfile Function(String nickname)? onSetNickname;

  UserProfile get _default => UserProfile(
        id: 3,
        nickname: '민수',
        status: UserStatus.active,
        onboardingCompleted: true,
        createdAt: DateTime.utc(2026, 7, 4),
      );

  @override
  Future<UserProfile> me() async => profile ?? _default;

  @override
  Future<UserProfile> setNickname(String nickname) async {
    final updated = onSetNickname?.call(nickname) ??
        UserProfile(
          id: 3,
          nickname: nickname,
          status: UserStatus.active,
          onboardingCompleted: true,
          createdAt: DateTime.utc(2026, 7, 4),
        );
    profile = updated;
    return updated;
  }

  @override
  Future<void> withdraw() async {}
}

class FakeAuthRepository implements AuthRepository {
  FakeAuthRepository({this.loginResponse});

  final LoginResponse? loginResponse;

  @override
  Future<LoginResponse> login(String kakaoAccessToken) async =>
      loginResponse ??
      LoginResponse(
        tokens: const TokenPair(accessToken: 'a', refreshToken: 'r'),
        tokenType: 'Bearer',
        expiresIn: 1800,
        isNewUser: true,
        user: const AuthUser(id: 3, nickname: '러너', onboardingCompleted: false),
      );

  @override
  Future<TokenPair?> refresh(String refreshToken) async => null;

  @override
  Future<void> logout() async {}
}
