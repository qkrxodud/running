package com.runningcrew.ranking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface RankEntryJpaRepository extends JpaRepository<RankEntryJpaEntity, Long> {
}
