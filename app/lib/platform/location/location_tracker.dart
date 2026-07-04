import '../../core/tracking/track_point.dart';

/// 트래커 상태머신 (플랫폼 무관). 클라이언트 상태머신
/// (READY→RUNNING→FINISHED_LOCAL→UPLOADED)의 트래킹 하위 상태다 —
/// FINISHED_LOCAL/UPLOADED 는 업로드 계층 소관이므로 여기 없다.
enum TrackerState { ready, running, paused, stopped }

/// 위치 트래킹 격리 지점 ① — OS 종속(Foreground Service/geolocator)을 이 인터페이스
/// 뒤로 숨긴다. iOS 확장 시 구현체(예: iOS 로컬 알림+CLLocationManager)만 추가되고
/// 상위 레이어는 무수정이어야 한다.
///
/// 구현체는 [start] 후 GPS 포인트를 수신 즉시 로컬 append 한 뒤 [points] 로
/// 방출한다(로컬 우선). [TrackPoint.timestamp] 는 GPS 시각 우선.
abstract interface class LocationTracker {
  /// 현재 상태(동기 조회). 초기값 [TrackerState.ready].
  TrackerState get state;

  /// 상태 변화 스트림(브로드캐스트). UI/상위 레이어 구독용.
  Stream<TrackerState> get stateChanges;

  /// 수신 즉시 방출되는 GPS 포인트 스트림(브로드캐스트).
  /// 구현체가 로컬 append 후 방출한다.
  Stream<TrackPoint> get points;

  /// 트래킹 시작. ready/stopped → running. 권한은 사전 보장 전제
  /// ([PermissionService] 로 확인 후 호출). running 중 재호출은 무시.
  Future<void> start();

  /// 일시정지. running → paused. 그로스 타임 원칙상 기록 시간은 계속 흐른다
  /// (자동 일시정지 아님) — pause 는 배터리/샘플링 완화용 훅이지 기록 정지가 아니다.
  Future<void> pause();

  /// 재개. paused → running.
  Future<void> resume();

  /// 종료. → stopped. 트래킹 중단만 — 도착점 확정·기록 절단은 서버 소관이다.
  Future<void> stop();

  /// 리소스 해제(스트림 컨트롤러 등).
  Future<void> dispose();
}
