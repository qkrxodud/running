package com.runningcrew.ranking.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 순위 산정 — <b>순수 함수</b>(ArchUnit R-1, 골든 대상 A7). 계획서 §5.4 규범:
 *
 * <ul>
 *   <li>완주자 {@code record_time_s} <b>오름차순</b>. 동률은 <b>공동순위 + 다음 건너뜀(1,1,3)</b>.
 *   <li>DNF/DNS는 rank NULL·목록 <b>하단</b>. 정렬: 완주(rank↑) → DNF → DNS.
 *   <li>PB: 완주자만. {@code priorPb==null || record < priorPb}. 첫 완주는 항상 PB. DNF/DNS는 false.
 * </ul>
 *
 * <p>동률·동상태 내부 정렬은 {@code userId} 오름차순으로 결정적이게 고정한다(골든 안정).
 */
public final class RankingPolicy {

    private RankingPolicy() {
    }

    public static List<RankedEntry> rank(List<RankingInput> inputs) {
        List<RankingInput> finishers = new ArrayList<>();
        List<RankingInput> dnf = new ArrayList<>();
        List<RankingInput> dns = new ArrayList<>();
        for (RankingInput in : inputs) {
            if (in.status() == ResultStatus.FINISHED && in.recordTimeS() != null) {
                finishers.add(in);
            } else if (in.status() == ResultStatus.DNS) {
                dns.add(in);
            } else {
                dnf.add(in);   // DNF(및 recordTime 없는 비정상 FINISHED 방어)
            }
        }

        finishers.sort(Comparator.comparingInt(RankingInput::recordTimeS)
                .thenComparing(RankingInput::userId));
        dnf.sort(Comparator.comparing(RankingInput::userId));
        dns.sort(Comparator.comparing(RankingInput::userId));

        List<RankedEntry> out = new ArrayList<>();
        int prevRank = 0;
        Integer prevTime = null;
        for (int i = 0; i < finishers.size(); i++) {
            RankingInput f = finishers.get(i);
            int rank;
            if (prevTime != null && f.recordTimeS().equals(prevTime)) {
                rank = prevRank;            // 동률 공동순위
            } else {
                rank = i + 1;               // 다음 순위 건너뜀
            }
            prevRank = rank;
            prevTime = f.recordTimeS();
            boolean isPb = f.priorPbTimeS() == null || f.recordTimeS() < f.priorPbTimeS();
            out.add(new RankedEntry(f.userId(), ResultStatus.FINISHED, rank, f.recordTimeS(), isPb));
        }
        for (RankingInput d : dnf) {
            out.add(new RankedEntry(d.userId(), ResultStatus.DNF, null, null, false));
        }
        for (RankingInput d : dns) {
            out.add(new RankedEntry(d.userId(), ResultStatus.DNS, null, null, false));
        }
        return out;
    }
}
