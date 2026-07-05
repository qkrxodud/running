package com.runningcrew.replay.application;

import com.runningcrew.replay.application.port.out.ReplaySnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 재생성 운영 유스케이스(A6, admin). 삭제→최신 스키마 재생성(멱등, RP-10). 원시 트랙 보존 하 순수함수라 입력이
 * 같으면 payload 동일·schema_version만 최신. FCM은 {@code replay_notified_at} 이미 set이면 재발송 안 함(RP-12).
 */
@Service
public class ReplayRegenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReplayRegenerationService.class);

    private final ReplaySnapshotRepository snapshotRepository;
    private final ReplayGenerationService generationService;

    public ReplayRegenerationService(ReplaySnapshotRepository snapshotRepository,
                                     ReplayGenerationService generationService) {
        this.snapshotRepository = snapshotRepository;
        this.generationService = generationService;
    }

    /**
     * 트랜잭션 미묶음 — deleteBySession(자체 tx 커밋) → generate(REQUIRES_NEW 자체 tx)로 <b>삭제 커밋 후
     * 재생성</b>. 원시 트랙(track_payload) 보존이라 재생성 소스 동일(멱등).
     */
    public void regenerate(Long sessionId) {
        log.info("replay_regenerate_requested session_id={}", sessionId);
        snapshotRepository.deleteBySession(sessionId);   // 삭제(자체 tx) → 최신 스키마 재생성
        generationService.generate(sessionId);
    }
}
