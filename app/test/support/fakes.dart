import 'package:running/app/auth_controller.dart';
import 'package:running/core/geo/lat_lng.dart';
import 'package:running/core/model/auth_dtos.dart';
import 'package:running/core/model/crew_dtos.dart';
import 'package:running/core/model/history_dtos.dart';
import 'package:running/core/model/page_response.dart';
import 'package:running/core/model/race_dtos.dart';
import 'package:running/core/model/replay_dtos.dart';
import 'package:running/core/model/track_dtos.dart';
import 'package:running/core/model/user_dtos.dart';
import 'package:running/data/auth_repository.dart';
import 'package:running/data/course_repository.dart';
import 'package:running/data/crew_repository.dart';
import 'package:running/data/history_repository.dart';
import 'package:running/data/replay_repository.dart';
import 'package:running/data/session_repository.dart';
import 'package:running/data/track_repository.dart';
import 'package:running/data/user_repository.dart';
import 'package:running/platform/auth/kakao_auth_service.dart';

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

/// 로그인 상태(프로필) 주입 — 크루장 판정 등 authControllerProvider 의존 위젯용.
class StubAuthController extends AuthController {
  StubAuthController(this.initial);
  final AuthState initial;

  @override
  AuthState build() => initial;
}

/// authenticated 상태 + 지정 userId 프로필의 Stub 컨트롤러 팩토리.
/// 사용: `authControllerProvider.overrideWith(() => stubAuthController(3))`.
StubAuthController stubAuthController(int userId, {String nickname = '나'}) {
  final profile = UserProfile(
    id: userId,
    nickname: nickname,
    status: UserStatus.active,
    onboardingCompleted: true,
    createdAt: DateTime.utc(2026, 7, 4),
  );
  return StubAuthController(
    AuthState(status: AuthStatus.authenticated, profile: profile),
  );
}

/// 코스 페이크 — 세션 생성 화면의 코스 선택 소스(시드 코스 대체).
class FakeCourseRepository implements CourseRepository {
  FakeCourseRepository({this.courses = const [], this.detail_, this.onPromote});

  final List<CourseSummary> courses;
  final CourseDetail? detail_;

  /// 승격 호출 관측·응답 주입. 인자: (crewId, sourceTrackRecordId, name).
  final CourseDetail Function(int crewId, int trackRecordId, String name)?
      onPromote;

  @override
  Future<PageResponse<CourseSummary>> listByCrew(int crewId,
      {int page = 0, int size = 20}) async {
    return PageResponse(
      items: courses,
      page: page,
      size: size,
      totalElements: courses.length,
      totalPages: 1,
    );
  }

  @override
  Future<CourseDetail> detail(int courseId) async =>
      detail_ ?? _stubCourse(courseId);

  @override
  Future<CourseDetail> create(
    int crewId, {
    required String name,
    required String routePolyline,
    required double startLat,
    required double startLng,
    required double finishLat,
    required double finishLng,
  }) async =>
      CourseDetail(
        id: 1,
        crewId: crewId,
        name: name,
        routePolyline: routePolyline,
        distanceM: 5000,
        start: LatLng(startLat, startLng),
        finish: LatLng(finishLat, finishLng),
      );

  @override
  Future<CourseDetail> promote(
    int crewId, {
    required int sourceTrackRecordId,
    required String name,
  }) async =>
      onPromote?.call(crewId, sourceTrackRecordId, name) ??
      CourseDetail(
        id: 200,
        crewId: crewId,
        name: name,
        routePolyline: '_p~iF~ps|U',
        distanceM: 5040,
        start: const LatLng(37.5121, 127.0018),
        finish: const LatLng(37.5288, 127.0219),
      );
}

/// 트랙 결과 조회 페이크 — 결과 화면(C1/C2)용.
class FakeTrackRepository implements TrackRepository {
  FakeTrackRepository({this.resultOutcome, this.myTrackResponse});

  final ResultQueryOutcome? resultOutcome;
  final TrackRecordResponse? myTrackResponse;

  @override
  Future<ResultQueryOutcome> result(int sessionId) async =>
      resultOutcome ?? const ResultPending();

  @override
  Future<TrackRecordResponse?> myTrack(int sessionId) async => myTrackResponse;

  @override
  Future<TrackRecordResponse> upload(
          int sessionId, TrackUploadRequest request) async =>
      throw UnimplementedError();
}

/// 히스토리·PB 페이크 — C5 화면용.
class FakeHistoryRepository implements HistoryRepository {
  FakeHistoryRepository({this.records = const [], this.pbs = const []});

  final List<RecordHistoryItem> records;
  final List<PersonalBestItem> pbs;

  @override
  Future<PageResponse<RecordHistoryItem>> myRecords(
          {int page = 0, int size = 20}) async =>
      PageResponse(
        items: records,
        page: page,
        size: size,
        totalElements: records.length,
        totalPages: 1,
      );

  @override
  Future<PageResponse<PersonalBestItem>> myPersonalBests(
          {int page = 0, int size = 20}) async =>
      PageResponse(
        items: pbs,
        page: page,
        size: size,
        totalElements: pbs.length,
        totalPages: 1,
      );
}

CourseDetail _stubCourse(int id) => CourseDetail(
      id: id,
      crewId: 12,
      name: '한강 5K',
      routePolyline: '_p~iF~ps|U_ulLnnqC_mqNvxq`@',
      distanceM: 5000,
      start: const LatLng(37.5121, 127.0018),
      finish: const LatLng(37.5288, 127.0219),
    );

/// 세션 페이크 — 명령(open/register/start/cancel)은 주입 콜백 또는 상태 전이 stub.
class FakeSessionRepository implements SessionRepository {
  FakeSessionRepository({
    this.sessions = const [],
    this.detail_,
    this.onCreate,
    this.onCommand,
  });

  final List<SessionSummary> sessions;
  final SessionDetail? detail_;
  final SessionDetail Function(int crewId, CreateSessionRequest req)? onCreate;
  final SessionDetail Function(String action, int sessionId)? onCommand;

  @override
  Future<PageResponse<SessionSummary>> listByCrew(int crewId,
      {int page = 0, int size = 20}) async {
    return PageResponse(
      items: sessions,
      page: page,
      size: size,
      totalElements: sessions.length,
      totalPages: 1,
    );
  }

  @override
  Future<SessionDetail> detail(int sessionId) async =>
      detail_ ?? _stubSession(sessionId, RaceStatus.open);

  @override
  Future<SessionDetail> create(int crewId, CreateSessionRequest request) async =>
      onCreate?.call(crewId, request) ??
      _stubSession(77, RaceStatus.draft, courseId: request.courseId);

  @override
  Future<SessionDetail> open(int sessionId) async =>
      onCommand?.call('open', sessionId) ??
      _stubSession(sessionId, RaceStatus.open);

  @override
  Future<SessionDetail> register(int sessionId) async =>
      onCommand?.call('register', sessionId) ??
      _stubSession(sessionId, RaceStatus.open);

  @override
  Future<SessionDetail> start(int sessionId) async =>
      onCommand?.call('start', sessionId) ??
      _stubSession(sessionId, RaceStatus.running);

  @override
  Future<SessionDetail> cancel(int sessionId) async =>
      onCommand?.call('cancel', sessionId) ??
      _stubSession(sessionId, RaceStatus.cancelled);
}

SessionDetail _stubSession(int id, RaceStatus status, {int courseId = 55}) =>
    SessionDetail(
      id: id,
      crewId: 12,
      course: _stubCourse(courseId),
      status: status,
      scheduledAt: DateTime.utc(2026, 7, 10, 21),
      uploadDeadline: DateTime.utc(2026, 7, 11, 9),
      participants: const [],
    );

class FakeAuthRepository implements AuthRepository {
  FakeAuthRepository({this.loginResponse, this.throwOnLogin});

  final LoginResponse? loginResponse;

  /// 주입 시 login 이 이 예외를 던진다(503 kapi 장애 UX 검증용).
  final Object? throwOnLogin;

  @override
  Future<LoginResponse> login(String kakaoAccessToken) async {
    if (throwOnLogin != null) throw throwOnLogin!;
    return loginResponse ??
        LoginResponse(
          tokens: const TokenPair(accessToken: 'a', refreshToken: 'r'),
          tokenType: 'Bearer',
          expiresIn: 1800,
          isNewUser: true,
          user:
              const AuthUser(id: 3, nickname: '러너', onboardingCompleted: false),
        );
  }

  @override
  Future<TokenPair?> refresh(String refreshToken) async => null;

  @override
  Future<void> logout() async {}
}

/// 카카오 로그인 경계 페이크 — 항상 [token] 을 반환(취소=null 도 주입 가능).
class FakeKakaoAuthService implements KakaoAuthService {
  const FakeKakaoAuthService({this.token = 'fake-kakao-token'});
  final String? token;

  @override
  Future<String?> login() async => token;
}

/// 리플레이 스냅샷 페이크 — 뷰어(M3-B) 상태별 UI 검증용.
class FakeReplayRepository implements ReplayRepository {
  FakeReplayRepository({this.response, this.throwOnSnapshot});

  final ReplaySnapshotResponse? response;
  final Object? throwOnSnapshot;

  @override
  Future<ReplaySnapshotResponse> snapshot(int sessionId) async {
    if (throwOnSnapshot != null) throw throwOnSnapshot!;
    return response ??
        const ReplaySnapshotResponse(
          status: ReplayStatus.generating,
          schemaVersion: null,
          displayNames: null,
          payload: null,
        );
  }
}
