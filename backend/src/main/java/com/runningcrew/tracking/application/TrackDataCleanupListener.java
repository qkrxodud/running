package com.runningcrew.tracking.application;

import com.runningcrew.tracking.application.port.out.TrackDataEraser;
import com.runningcrew.user.domain.event.UserWithdrawn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 컨텍스트 간 이벤트 소비: {@link UserWithdrawn} → 위치 원본(track_payload) 파기(설계 §1.3 step5).
 *
 * <p><b>동기 @EventListener(같은 트랜잭션)</b> — 탈퇴와 원본 파기가 원자적이어야 한다. user→tracking
 * 직접 호출 없이 이벤트 경유만(ArchUnit R-2: user.domain.event만 import).
 */
@Service
public class TrackDataCleanupListener {

    private final TrackDataEraser trackDataEraser;

    public TrackDataCleanupListener(TrackDataEraser trackDataEraser) {
        this.trackDataEraser = trackDataEraser;
    }

    @EventListener
    public void on(UserWithdrawn event) {
        trackDataEraser.eraseByUserId(event.userId());
    }
}
