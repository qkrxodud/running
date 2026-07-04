package com.runningcrew.race.application.port.out;

import java.util.Optional;

/**
 * 크루 접근 정보 out-port(권한·멤버십). Race 컨텍스트가 <b>크루 컨텍스트 클래스를 참조하지 않고</b>
 * 크루장·CLOSED·ACTIVE 멤버 여부만 얻기 위한 경계 — 어댑터는 crew/crew_member 테이블 네이티브 SQL로
 * 구현한다(ArchUnit R-2: 컨텍스트 간 클래스 의존 없음).
 */
public interface CrewAccessPort {

    Optional<CrewRef> findCrew(Long crewId);

    boolean isActiveMember(Long crewId, Long userId);

    /** 권한 판정에 필요한 크루 최소 정보(race 컨텍스트 로컬 표현). */
    record CrewRef(Long crewId, Long leaderId, boolean closed) {

        public boolean isLeader(Long userId) {
            return leaderId != null && leaderId.equals(userId);
        }
    }
}
