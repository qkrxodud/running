package com.runningcrew.crew.adapter.out.persistence;

import com.runningcrew.crew.application.view.CrewSummaryView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CrewJpaRepository extends JpaRepository<CrewJpaEntity, Long> {

    @Query("select distinct c from CrewJpaEntity c left join fetch c.members where c.id = :id")
    Optional<CrewJpaEntity> findWithMembersById(@Param("id") Long id);

    @Query("select distinct c from CrewJpaEntity c left join fetch c.members "
            + "where c.id in (select m.crew.id from CrewMemberJpaEntity m "
            + "where m.userId = :userId and m.status = com.runningcrew.crew.domain.CrewMemberStatus.ACTIVE)")
    List<CrewJpaEntity> findAllWithMembersByActiveMemberUserId(@Param("userId") Long userId);

    // 내 크루 목록(계약 §2): member_count=ACTIVE 멤버 수, role=요청자 역할. user 조인 불필요(닉네임 없음).
    @Query(value = "select new com.runningcrew.crew.application.view.CrewSummaryView("
            + "c.id, c.name, c.status, "
            + "(select count(m2) from CrewMemberJpaEntity m2 where m2.crew = c "
            + "  and m2.status = com.runningcrew.crew.domain.CrewMemberStatus.ACTIVE), "
            + "m.role, c.createdAt) "
            + "from CrewJpaEntity c join c.members m "
            + "where m.userId = :userId and m.status = com.runningcrew.crew.domain.CrewMemberStatus.ACTIVE",
            countQuery = "select count(m) from CrewMemberJpaEntity m "
            + "where m.userId = :userId and m.status = com.runningcrew.crew.domain.CrewMemberStatus.ACTIVE")
    Page<CrewSummaryView> findMyCrews(@Param("userId") Long userId, Pageable pageable);
}
