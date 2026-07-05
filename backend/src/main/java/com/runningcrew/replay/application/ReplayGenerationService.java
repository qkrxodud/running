package com.runningcrew.replay.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.notification.application.port.out.NotificationSender;
import com.runningcrew.notification.domain.NotificationMessage;
import com.runningcrew.notification.domain.NotificationMessage.NotificationType;
import com.runningcrew.replay.application.port.out.ReplayNotificationGate;
import com.runningcrew.replay.application.port.out.ReplaySnapshotRepository;
import com.runningcrew.replay.application.port.out.ReplaySourcePort;
import com.runningcrew.replay.application.port.out.ReplaySourcePort.GenerationSource;
import com.runningcrew.replay.application.port.out.ReplaySourcePort.ParticipantSource;
import com.runningcrew.replay.domain.ColorParams;
import com.runningcrew.replay.domain.MergedTimeline;
import com.runningcrew.replay.domain.Overtake;
import com.runningcrew.replay.domain.OvertakeCalculator;
import com.runningcrew.replay.domain.ReplayFrame;
import com.runningcrew.replay.domain.ReplayMerger;
import com.runningcrew.replay.domain.ReplayParticipant;
import com.runningcrew.replay.domain.ReplaySegmentColor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리플레이 스냅샷 생성(A4). GENERATING 저장 → 순수 병합·추월·색상 계산 → payload 조립 → READY(payload) /
 * 예외 시 FAILED(원인 로그, RP-8). 스냅샷 크기 상한(2MiB) 초과도 FAILED(RP-13). 최초 READY면 알림 게이트를
 * 통해 세션당 1회 FCM 트리거(RP-12). refined 접근은 {@link ReplaySourcePort}(RP-1) — 조회 어댑터 미주입.
 */
@Service
public class ReplayGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReplayGenerationService.class);

    private final ReplaySourcePort sourcePort;
    private final ReplaySnapshotRepository snapshotRepository;
    private final ReplayNotificationGate notificationGate;
    private final NotificationSender notificationSender;
    private final ObjectMapper objectMapper;
    private final RefinedTrackParser parser;
    private final int schemaVersion;
    private final int maxBytes;

    public ReplayGenerationService(ReplaySourcePort sourcePort,
                                   ReplaySnapshotRepository snapshotRepository,
                                   ReplayNotificationGate notificationGate,
                                   NotificationSender notificationSender,
                                   ObjectMapper objectMapper,
                                   @Value("${replay.snapshot.schema-version:1}") int schemaVersion,
                                   @Value("${replay.snapshot.max-bytes:2097152}") int maxBytes) {
        this.sourcePort = sourcePort;
        this.snapshotRepository = snapshotRepository;
        this.notificationGate = notificationGate;
        this.notificationSender = notificationSender;
        this.objectMapper = objectMapper;
        this.parser = new RefinedTrackParser(objectMapper);
        this.schemaVersion = schemaVersion;
        this.maxBytes = maxBytes;
    }

    /**
     * 세션 스냅샷 1건 생성(신규 GENERATING 행). AFTER_COMMIT 리스너·재생성 admin 모두 이 경로를 쓴다.
     * REQUIRES_NEW — AFTER_COMMIT 컨텍스트에서 새 트랜잭션으로 격리(확정 트랜잭션과 분리, RP-9).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generate(Long sessionId) {
        GenerationSource source = sourcePort.loadGenerationSource(sessionId).orElse(null);
        if (source == null || source.participants().isEmpty()) {
            log.info("replay_generation_skipped session_id={} reason=no_source", sessionId);
            return;   // CANCELLED·트랙 부재 세션 등 — 스냅샷 미생성(조회 시 404)
        }

        Long snapshotId = snapshotRepository.saveGenerating(sessionId, schemaVersion);
        try {
            String payloadJson = buildPayload(sessionId, source);
            int bytes = payloadJson.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maxBytes) {
                throw new IllegalStateException("스냅샷 크기 상한 초과: " + bytes + " > " + maxBytes);
            }
            snapshotRepository.markReady(snapshotId, payloadJson);
            log.info("replay_generation_ready session_id={} snapshot_id={} bytes={}",
                    sessionId, snapshotId, bytes);
            notifyReplayOpenedIfFirst(sessionId);
        } catch (Exception e) {
            snapshotRepository.markFailed(snapshotId);
            log.error("replay_generation_failed session_id={} snapshot_id={} cause={}",
                    sessionId, snapshotId, e.toString(), e);   // 조용한 실패 금지(RP-8)
        }
    }

    private String buildPayload(Long sessionId, GenerationSource source) throws Exception {
        List<com.runningcrew.replay.domain.ReplayTrackInput> tracks = new ArrayList<>();
        for (ParticipantSource p : source.participants()) {
            tracks.add(parser.parse(p));
        }
        MergedTimeline merged = ReplayMerger.mergeToRelativeTimeline(tracks, ColorParams.defaults());
        List<Overtake> overtakes = OvertakeCalculator.computeOvertakes(merged.participants());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", schemaVersion);
        root.put("session_id", sessionId);

        Map<String, Object> course = new LinkedHashMap<>();
        course.put("distance_m", source.courseDistanceM());
        course.put("route_polyline", source.coursePolyline());
        course.put("start", latLng(source.startLat(), source.startLng()));
        course.put("finish", latLng(source.finishLat(), source.finishLng()));
        root.put("course", course);

        root.put("duration_ms", merged.durationMs());
        root.put("participants", merged.participants().stream().map(this::participantJson).toList());
        root.put("overtakes", overtakes.stream().map(o -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at_dist_m", o.atDistM());
            m.put("passer_user_id", o.passerUserId());
            m.put("passed_user_id", o.passedUserId());
            m.put("t_ms", o.tMs());
            return m;
        }).toList());

        return objectMapper.writeValueAsString(root);
    }

    private Map<String, Object> participantJson(ReplayParticipant p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", p.userId());
        m.put("finish_status", p.finishStatus());
        m.put("finish_time_ms", p.finishTimeMs());   // DNF null(non_null 직렬화로 생략)
        m.put("frames", p.frames().stream().map(this::frameJson).toList());
        m.put("segments", p.segments().stream().map(this::segmentJson).toList());
        return m;
    }

    private Map<String, Object> frameJson(ReplayFrame f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t_ms", f.tMs());
        m.put("lat", f.lat());
        m.put("lng", f.lng());
        m.put("cum_dist_m", f.cumDistM());
        m.put("is_gap", f.isGap());
        return m;
    }

    private Map<String, Object> segmentJson(ReplaySegmentColor s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seg_index", s.segIndex());
        m.put("start_dist_m", s.startDistM());
        m.put("end_dist_m", s.endDistM());
        m.put("pace_s_per_km", s.paceSPerKm());
        m.put("color_bucket", s.colorBucket());
        return m;
    }

    private static Map<String, Object> latLng(double lat, double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", lat);
        m.put("lng", lng);
        return m;
    }

    /** 최초 READY만 발송(세션당 1회 멱등 — 재생성 재발송 금지, RP-12). */
    private void notifyReplayOpenedIfFirst(Long sessionId) {
        if (!notificationGate.markNotifiedIfFirst(sessionId)) {
            return;   // 이미 발송됨(재생성·중복) — no-op
        }
        NotificationMessage message = new NotificationMessage(
                NotificationType.REPLAY_READY, sessionId,
                notificationGate.participantUserIds(sessionId),
                "리플레이가 준비됐어요", "레이스 리플레이를 확인해 보세요.",
                Map.of("deep_link", "runningcrew://replay/" + sessionId));
        notificationSender.send(message);
    }
}
