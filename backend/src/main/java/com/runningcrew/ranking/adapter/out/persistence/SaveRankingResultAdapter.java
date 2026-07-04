package com.runningcrew.ranking.adapter.out.persistence;

import com.runningcrew.ranking.application.port.out.SaveRankingResultPort;
import com.runningcrew.ranking.domain.RankedEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;

/** {@link SaveRankingResultPort} 구현 — race_result + rank_entry 저장. */
@Repository
public class SaveRankingResultAdapter implements SaveRankingResultPort {

    private final RaceResultJpaRepository resultJpa;
    private final RankEntryJpaRepository entryJpa;

    public SaveRankingResultAdapter(RaceResultJpaRepository resultJpa,
                                    RankEntryJpaRepository entryJpa) {
        this.resultJpa = resultJpa;
        this.entryJpa = entryJpa;
    }

    @Override
    public Long save(Long sessionId, Instant finalizedAt, List<RankedEntry> entries) {
        RaceResultJpaEntity result =
                resultJpa.saveAndFlush(new RaceResultJpaEntity(null, sessionId, finalizedAt));
        for (RankedEntry e : entries) {
            entryJpa.save(new RankEntryJpaEntity(null, result.getId(), e.userId(), e.rank(),
                    e.recordTimeS(), e.isPb()));
        }
        entryJpa.flush();
        return result.getId();
    }
}
