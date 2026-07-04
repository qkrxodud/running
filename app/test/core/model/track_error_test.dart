import 'package:flutter_test/flutter_test.dart';
import 'package:running/core/model/api_error.dart';
import 'package:running/core/model/track_error.dart';

/// R-007 잔여 조건 — 업로드 오류 **code 기반 분기**(403 비멤버 / 409 미등록·상태·중복).
/// 메시지 매칭 없이 code 만으로 배타 분류함을 박제.
void main() {
  ApiException ex(int status, String code) =>
      ApiException(statusCode: status, code: code, message: '메시지 무관');

  group('403(비멤버) / 409(미등록·상태·중복) 배타 분기', () {
    test('403 FORBIDDEN → forbiddenNotMember', () {
      expect(classifyTrackUploadError(ex(403, 'FORBIDDEN')),
          TrackUploadErrorKind.forbiddenNotMember);
    });

    test('409 SESSION_STATE_INVALID → notRegisteredOrBadState', () {
      expect(classifyTrackUploadError(ex(409, 'SESSION_STATE_INVALID')),
          TrackUploadErrorKind.notRegisteredOrBadState);
    });

    test('409 TRACK_ALREADY_UPLOADED → alreadyUploaded (미등록과 구별)', () {
      expect(classifyTrackUploadError(ex(409, 'TRACK_ALREADY_UPLOADED')),
          TrackUploadErrorKind.alreadyUploaded);
    });

    test('403 과 409 는 서로 다른 분류(동일 호출자 배타)', () {
      final forbidden = classifyTrackUploadError(ex(403, 'FORBIDDEN'));
      final conflict =
          classifyTrackUploadError(ex(409, 'SESSION_STATE_INVALID'));
      expect(forbidden, isNot(conflict));
    });
  });

  group('400 계열 / 413 / 404', () {
    test('payload/array/validation → payloadInvalid', () {
      expect(classifyTrackUploadError(ex(400, 'TRACK_PAYLOAD_INVALID')),
          TrackUploadErrorKind.payloadInvalid);
      expect(classifyTrackUploadError(ex(400, 'TRACK_ARRAY_LENGTH_MISMATCH')),
          TrackUploadErrorKind.payloadInvalid);
      expect(classifyTrackUploadError(ex(400, 'VALIDATION_ERROR')),
          TrackUploadErrorKind.payloadInvalid);
    });
    test('413 TRACK_TOO_LARGE → tooLarge', () {
      expect(classifyTrackUploadError(ex(413, 'TRACK_TOO_LARGE')),
          TrackUploadErrorKind.tooLarge);
    });
    test('404 NOT_FOUND → notFound', () {
      expect(classifyTrackUploadError(ex(404, 'NOT_FOUND')),
          TrackUploadErrorKind.notFound);
    });
  });

  group('재시도 정책 — 일시적 장애만 재시도', () {
    test('네트워크 단절(statusCode 0) → network, 재시도 가능', () {
      final kind = classifyTrackUploadError(
          const ApiException(statusCode: 0, code: 'NETWORK_ERROR', message: ''));
      expect(kind, TrackUploadErrorKind.network);
      expect(kind.isRetryable, isTrue);
    });

    test('5xx → server, 재시도 가능', () {
      final kind = classifyTrackUploadError(ex(503, 'UNKNOWN'));
      expect(kind, TrackUploadErrorKind.server);
      expect(kind.isRetryable, isTrue);
    });

    test('4xx 클라 오류는 전부 재시도 불가', () {
      expect(TrackUploadErrorKind.forbiddenNotMember.isRetryable, isFalse);
      expect(TrackUploadErrorKind.notRegisteredOrBadState.isRetryable, isFalse);
      expect(TrackUploadErrorKind.alreadyUploaded.isRetryable, isFalse);
      expect(TrackUploadErrorKind.payloadInvalid.isRetryable, isFalse);
      expect(TrackUploadErrorKind.tooLarge.isRetryable, isFalse);
    });
  });

  test('계약 밖 code 는 상태코드로 보강, 그래도 미확정이면 unknown(종단)', () {
    expect(classifyTrackUploadError(ex(403, 'WEIRD_CODE')),
        TrackUploadErrorKind.forbiddenNotMember);
    expect(classifyTrackUploadError(ex(418, 'WEIRD_CODE')),
        TrackUploadErrorKind.unknown);
    expect(TrackUploadErrorKind.unknown.isRetryable, isFalse);
  });
}
