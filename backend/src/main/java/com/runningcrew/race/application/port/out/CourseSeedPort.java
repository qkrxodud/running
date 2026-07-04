package com.runningcrew.race.application.port.out;

import java.util.Optional;

/**
 * dev 시드용 조회 out-port(설계 §1.4 — 미규정-1). 시드 대상 크루 선정과 멱등(중복 방지) 판정만 제공한다.
 * 어댑터는 crew/course 테이블 네이티브 SQL로 구현(크루 컨텍스트 클래스 미참조 — R-2).
 */
public interface CourseSeedPort {

    /** 시드 대상 = 가장 먼저 생성된 ACTIVE 크루(그 크루장). 없으면 empty(no-op). */
    Optional<SeedTargetCrew> findSeedTargetCrew();

    /** 같은 크루에 같은 이름 코스가 이미 있으면 true(재기동 중복 방지). */
    boolean courseExists(Long crewId, String name);

    record SeedTargetCrew(Long crewId, Long leaderId) {
    }
}
