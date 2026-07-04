package com.runningcrew.race.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SessionClosePolicy 순수 함수 <b>seed 테스트</b>(A9) — clock 주입으로 deadline 전이 재현. 경계 확장은
 * test-engineer 소관. 계획서 §8·설계 42 §6: 전원 업로드 OR deadline, STARTED미업로드→DNF, REGISTERED→DNS.
 */
class SessionClosePolicyTest {

    private static final Instant DEADLINE = Instant.parse("2026-07-11T09:00:00Z");

    @Test
    void deadline_이전_STARTED_미업로드_존재하면_확정하지_않는다() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.STARTED, true, true, 600, 5000),
                close(2L, ParticipationStatus.STARTED, false, false, null, null));
        Instant before = Instant.parse("2026-07-11T08:00:00Z");
        assertThat(SessionClosePolicy.shouldFinalize(ps, before, DEADLINE)).isFalse();
    }

    @Test
    void STARTED_전원_업로드면_deadline_이전에도_확정() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.STARTED, true, true, 600, 5000),
                close(2L, ParticipationStatus.STARTED, true, false, null, 3000));
        Instant before = Instant.parse("2026-07-11T08:00:00Z");
        assertThat(SessionClosePolicy.shouldFinalize(ps, before, DEADLINE)).isTrue();
    }

    @Test
    void deadline_도달하면_미업로드가_있어도_확정() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.STARTED, false, false, null, null));
        Instant at = Instant.parse("2026-07-11T09:00:00Z");   // now == deadline (≥)
        assertThat(SessionClosePolicy.shouldFinalize(ps, at, DEADLINE)).isTrue();
    }

    @Test
    void 최종화_STARTED미업로드_DNF_REGISTERED_DNS_완주_FINISHED() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.STARTED, true, true, 600, 5000),    // 완주
                close(2L, ParticipationStatus.STARTED, true, false, null, 3000),  // 업로드·미완주
                close(3L, ParticipationStatus.STARTED, false, false, null, null),// 미업로드
                close(4L, ParticipationStatus.REGISTERED, false, false, null, null), // 미출주
                close(5L, ParticipationStatus.WITHDRAWN, false, false, null, null)); // 제외
        List<ParticipantOutcome> out = SessionClosePolicy.finalize(ps);
        assertThat(out).extracting(ParticipantOutcome::userId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(out).extracting(ParticipantOutcome::finalStatus).containsExactly(
                ParticipationStatus.FINISHED, ParticipationStatus.DNF,
                ParticipationStatus.DNF, ParticipationStatus.DNS);
        assertThat(out.get(0).recordTimeS()).isEqualTo(600);
        assertThat(out.get(1).totalDistanceM()).isEqualTo(3000);   // DNF도 뛴 거리 보존
        assertThat(out.get(3).totalDistanceM()).isNull();          // DNS 트랙 없음
    }

    private static ParticipantClose close(long userId, ParticipationStatus s, boolean hasTrack,
                                          boolean finished, Integer time, Integer dist) {
        return new ParticipantClose(userId, s, hasTrack, finished, time, dist);
    }
}
