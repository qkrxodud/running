package com.runningcrew.tracking.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.tracking.application.port.out.LoadTrackRecordPort;
import com.runningcrew.tracking.application.port.out.SaveTrackPort;
import com.runningcrew.tracking.application.port.out.TrackUploadSupportPort;
import com.runningcrew.tracking.application.view.TrackRecordSummary;
import com.runningcrew.tracking.domain.CourseShape;
import com.runningcrew.tracking.domain.FinishJudgment;
import com.runningcrew.tracking.domain.FinishParams;
import com.runningcrew.tracking.domain.FinishPolicy;
import com.runningcrew.tracking.domain.InvalidTrackPayloadException;
import com.runningcrew.tracking.domain.RefinedTrack;
import com.runningcrew.tracking.domain.RefinementParams;
import com.runningcrew.tracking.domain.SegmentParams;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackPoint;
import com.runningcrew.tracking.domain.TrackPolylineCodec;
import com.runningcrew.tracking.domain.TrackRecord;
import com.runningcrew.tracking.domain.TrackRefinementService;
import com.runningcrew.tracking.domain.TrackSegment;
import com.runningcrew.tracking.domain.TrackSegmentService;
import com.runningcrew.tracking.domain.event.TrackUploaded;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트랙 업로드 유스케이스(track-api §1). <b>디코딩 + 시간 정합성 검증 + 정제 + 완주 판정 + 저장</b>까지.
 *
 * <ul>
 *   <li><b>코스 이탈 검증 없음</b>(FP-4 — FinishPolicy 일원화). A3는 시간 정합성만.
 *   <li>멱등(O-M2-4): 동일 {@code client_upload_id} 재요청=기존 결과(200), 다른 내용=409 TRACK_ALREADY_UPLOADED.
 *   <li>track_record(요약)/track_payload(블롭) 분리 저장. 조회 포트는 블롭 미로드(TR-3).
 *   <li>커밋 후 {@link TrackUploaded} 발행 → Race가 AFTER_COMMIT로 전원 업로드 재평가(A10).
 * </ul>
 */
@Service
public class TrackUploadService {

    private static final int MAX_POINTS = 20_000;
    // 수신 시각 대비 과도한 미래만 거부(경미한 시계 편차 허용). 외부화 여지.
    private static final long FUTURE_TOLERANCE_MS = 86_400_000L;   // 1일
    private static final Set<String> ALLOWED_META_KEYS =
            Set.of("os", "os_version", "device_model");

    private final TrackUploadSupportPort supportPort;
    private final LoadTrackRecordPort loadPort;
    private final SaveTrackPort savePort;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TrackUploadService(TrackUploadSupportPort supportPort, LoadTrackRecordPort loadPort,
                              SaveTrackPort savePort, ApplicationEventPublisher eventPublisher,
                              ObjectMapper objectMapper, Clock clock) {
        this.supportPort = supportPort;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public UploadOutcome upload(Long userId, Long sessionId, TrackUploadCommand command) {
        // 1) 세션·권한·참가·확정 가드 (평가 순서 404→403→409, R-007/W46-2 배타)
        String status = supportPort.findSessionStatus(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));   // ① 세션 없음 → 404
        // ② 세션 소유 크루 ACTIVE 멤버 아님 → 403 (participation 409보다 앞 — 세션 존재·상태 누설 금지)
        if (!supportPort.isActiveCrewMember(sessionId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        // ③ 상태 부적합 / 확정 완료 / 미등록 → 409 (크루 멤버에게만 노출).
        // CANCELLED 수락(track-api v0.1.2 / C7): 취소 세션 트랙도 개인 기록으로 보존(계획서 §5.2).
        // 거부: COMPLETED(확정 후 불변)·DRAFT(미발행). CANCELLED은 순위·PB 미트리거(마감 파이프라인 no-op).
        if (!("OPEN".equals(status) || "RUNNING".equals(status) || "FINALIZING".equals(status)
                || "CANCELLED".equals(status))) {
            throw new ApiException(ErrorCode.SESSION_STATE_INVALID,
                    "이 세션 상태(" + status + ")에서는 업로드할 수 없습니다.");
        }
        if (supportPort.resultExists(sessionId)) {
            throw new ApiException(ErrorCode.SESSION_STATE_INVALID, "이미 결과가 확정된 세션입니다.");
        }
        if (!supportPort.participationExists(sessionId, userId)) {
            throw new ApiException(ErrorCode.SESSION_STATE_INVALID, "선 참가 신청이 필요합니다.");
        }

        // 2) 멱등 — 이미 업로드된 participation
        Optional<TrackRecordSummary> existing =
                loadPort.findBySessionIdAndUserId(sessionId, userId);
        if (existing.isPresent()) {
            TrackRecordSummary prev = existing.get();
            if (prev.clientUploadId() != null
                    && prev.clientUploadId().equals(command.clientUploadId())) {
                return new UploadOutcome(prev, false);   // 동일 키 재요청 → 200
            }
            throw new ApiException(ErrorCode.TRACK_ALREADY_UPLOADED);   // 다른 내용 → 409
        }

        // 3) 페이로드 검증(TK-1~4)
        List<TrackPoint> raw = parseAndValidate(command);

        // 4) 정제 → 완주/DNF 판정(FinishPolicy 일원화)
        RefinedTrack refined = TrackRefinementService.refine(raw, RefinementParams.defaults());
        CourseShape course = supportPort.findCourseShape(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "세션 코스를 찾을 수 없습니다."));
        FinishJudgment judgment = FinishPolicy.judge(refined, course, FinishParams.defaults());

        // 5) 레코드 조립 + 블롭 저장
        TrackRecord record = TrackRecord.create(sessionId, userId, command.clientUploadId(),
                command.startedAt(), refined, judgment);
        String refinedPayload = buildRefinedPayload(refined);
        Long id = savePort.save(record, refined.gapCount(), command.rawPayloadJson(), refinedPayload);

        // 6) 커밋 후 Race 통지(전원 업로드 재평가)
        eventPublisher.publishEvent(new TrackUploaded(sessionId, userId));

        TrackRecordSummary summary = new TrackRecordSummary(id, sessionId, userId,
                command.clientUploadId(), record.getStartedAt(), record.getFinishedAt(),
                record.getTotalDistanceM(), record.getTotalTimeS(), refined.gapCount());
        return new UploadOutcome(summary, true);
    }

    private List<TrackPoint> parseAndValidate(TrackUploadCommand c) {
        List<TrackCoord> coords;
        try {
            coords = TrackPolylineCodec.decode(c.polyline());
        } catch (InvalidTrackPayloadException e) {
            throw new ApiException(ErrorCode.TRACK_PAYLOAD_INVALID, e.getMessage());
        }
        if (coords.size() < 2) {
            throw new ApiException(ErrorCode.TRACK_PAYLOAD_INVALID, "폴리라인은 최소 2점이어야 합니다.");
        }
        if (coords.size() > MAX_POINTS) {
            throw new ApiException(ErrorCode.TRACK_TOO_LARGE);
        }
        int n = coords.size();
        // TK-1 배열 길이 일치
        if (c.timestamps() == null || c.speeds() == null || c.accuracies() == null
                || c.timestamps().length != n || c.speeds().length != n
                || c.accuracies().length != n
                || (c.altitudes() != null && c.altitudes().length != n)) {
            throw new ApiException(ErrorCode.TRACK_ARRAY_LENGTH_MISMATCH);
        }
        // TK-2 시간 정합성(비내림차순 + 미래 아님)
        long nowMs = clock.millis();
        long[] ts = c.timestamps();
        for (int i = 0; i < n; i++) {
            if (ts[i] > nowMs + FUTURE_TOLERANCE_MS) {
                throw new ApiException(ErrorCode.TRACK_PAYLOAD_INVALID, "미래 시각 타임스탬프입니다.");
            }
            if (i > 0 && ts[i] < ts[i - 1]) {
                throw new ApiException(ErrorCode.TRACK_PAYLOAD_INVALID, "타임스탬프가 역순입니다.");
            }
        }
        List<TrackPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Double alt = c.altitudes() != null ? c.altitudes()[i] : null;
            TrackCoord co = coords.get(i);
            points.add(new TrackPoint(co.lat(), co.lng(), ts[i], c.speeds()[i], c.accuracies()[i],
                    alt));
        }
        return points;
    }

    /** client_meta 허용 키 검증(TK-4) — web DTO 파싱 뒤 애플리케이션에서 재확인(3키만). */
    public static void validateClientMetaKeys(Map<String, Object> clientMeta) {
        if (clientMeta == null) {
            return;
        }
        for (String key : clientMeta.keySet()) {
            if (!ALLOWED_META_KEYS.contains(key)) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR,
                        "허용되지 않은 client_meta 키: " + key);
            }
        }
    }

    private String buildRefinedPayload(RefinedTrack refined) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("polyline", TrackPolylineCodec.encode(refined.coords()));
        root.put("timestamps", refined.points().stream().map(TrackPoint::tsMillis).toList());
        root.put("total_distance_m", refined.totalDistanceM());
        List<Map<String, Object>> gaps = refined.gaps().stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start_index", g.startIndex());
            m.put("end_index", g.endIndex());
            m.put("delta_ms", g.deltaMillis());
            return m;
        }).toList();
        root.put("gps_gaps", gaps);
        List<TrackSegment> segments =
                TrackSegmentService.segments(refined, SegmentParams.defaults());
        List<Map<String, Object>> segs = segments.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", s.index());
            m.put("start_distance_m", s.startDistanceM());
            m.put("end_distance_m", s.endDistanceM());
            m.put("duration_s", s.durationS());
            m.put("avg_pace_s_per_km", s.avgPaceSPerKm());
            return m;
        }).toList();
        root.put("segments", segs);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("정제 페이로드 직렬화 실패", e);
        }
    }
}
