package com.runningcrew.crew.application;

import com.runningcrew.crew.domain.event.CrewMemberJoined;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link CrewMemberJoined}의 유일한 소비자(로그) — O-1 인앱 갈음 확정에 따라 FCM·알림함 없음.
 * 발행 구조를 보존해 결정 번복 시 소비자만 추가하면 되게 한다(설계 §4).
 */
@Component
public class CrewMemberJoinedLogListener {

    private static final Logger log = LoggerFactory.getLogger(CrewMemberJoinedLogListener.class);

    @EventListener
    public void on(CrewMemberJoined event) {
        log.debug("CrewMemberJoined crewId={} userId={}", event.crewId(), event.userId());
    }
}
