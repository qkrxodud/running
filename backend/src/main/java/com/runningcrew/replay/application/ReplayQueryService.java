package com.runningcrew.replay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.replay.application.port.out.ReplayQueryPort;
import com.runningcrew.replay.application.port.out.ReplaySnapshotRepository;
import com.runningcrew.replay.application.port.out.ReplaySnapshotRepository.SnapshotRow;
import com.runningcrew.replay.application.view.ReplaySnapshotView;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리플레이 조회(A8). 세션·크루 멤버 검증 후 최신 스냅샷을 status별로 반환한다(READY만 payload + 표시명 조인).
 * READY 응답 시 <b>조회 이벤트 구조화 로그</b>(A9 — M4 24h 조회율 측정, user_id만·익명 파생, RP-14).
 */
@Service
public class ReplayQueryService {

    private static final Logger viewLog = LoggerFactory.getLogger("replay.view");

    private final ReplaySnapshotRepository snapshotRepository;
    private final ReplayQueryPort queryPort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReplayQueryService(ReplaySnapshotRepository snapshotRepository,
                              ReplayQueryPort queryPort, ObjectMapper objectMapper, Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.queryPort = queryPort;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReplaySnapshotView getReplay(Long userId, Long sessionId) {
        if (!queryPort.sessionExists(sessionId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!queryPort.isCrewMember(sessionId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        SnapshotRow row = snapshotRepository.findLatestBySession(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));   // 스냅샷 미생성 → 404

        if (!"READY".equals(row.status())) {
            return ReplaySnapshotView.notReady(row.status());   // GENERATING/FAILED — 상태만
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(row.payloadJson());
        } catch (Exception e) {
            throw new IllegalStateException("스냅샷 payload 파싱 실패", e);
        }
        Map<Long, String> displayNames = queryPort.displayNames(participantUserIds(payload));
        logView(sessionId, userId);
        return new ReplaySnapshotView("READY", row.schemaVersion(), displayNames, payload);
    }

    private List<Long> participantUserIds(JsonNode payload) {
        List<Long> ids = new ArrayList<>();
        JsonNode participants = payload.get("participants");
        if (participants != null) {
            for (JsonNode p : participants) {
                ids.add(p.get("user_id").asLong());
            }
        }
        return ids;
    }

    /** 조회 이벤트 구조화 로그(A9). viewed_within_24h = viewed_at − finalized_at ≤ 24h. */
    private void logView(Long sessionId, Long userId) {
        Instant viewedAt = clock.instant();
        Instant finalizedAt = queryPort.findFinalizedAt(sessionId).orElse(null);
        Boolean within24h = finalizedAt == null ? null
                : Duration.between(finalizedAt, viewedAt).compareTo(Duration.ofHours(24)) <= 0;
        viewLog.info("replay_viewed session_id={} user_id={} viewed_at={} finalized_at={} "
                + "viewed_within_24h={}", sessionId, userId, viewedAt, finalizedAt, within24h);
    }
}
