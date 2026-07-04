package com.runningcrew.race.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionClosePolicy} 경계 카탈로그 골든(설계 42 §6). seed({@code SessionClosePolicyTest}: 전원업로드/
 * deadline 확정, STARTED미업로드→DNF·REGISTERED→DNS·WITHDRAWN 제외)가 커버 못 한 <b>갭만</b> 추가한다.
 * clock 은 주입(now)으로 재현. 계획서 §8.
 */
class SessionClosePolicyGoldenTest {

    private static final Instant DEADLINE = Instant.parse("2026-07-11T09:00:00Z");

    private static ParticipantClose close(long userId, ParticipationStatus s, boolean hasTrack,
                                          boolean finished, Integer time, Integer dist) {
        return new ParticipantClose(userId, s, hasTrack, finished, time, dist);
    }

    @Test
    @DisplayName("deadline 1ms 직전 + STARTED 미업로드 → 아직 확정 안 함")
    void deadline_1ms_직전_미확정() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.STARTED, false, false, null, null));
        assertThat(SessionClosePolicy.shouldFinalize(ps, DEADLINE.minusMillis(1), DEADLINE))
                .isFalse();
    }

    @Test
    @DisplayName("deadline 정확히 도달(등호) → 미업로드가 있어도 확정")
    void deadline_정확히_도달_확정() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.STARTED, false, false, null, null));
        assertThat(SessionClosePolicy.shouldFinalize(ps, DEADLINE, DEADLINE)).isTrue();
    }

    @Test
    @DisplayName("STARTED 없이 REGISTERED만 있고 deadline 전이면 확정 안 함")
    void STARTED_없으면_deadline전_미확정() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.REGISTERED, false, false, null, null),
                close(2L, ParticipationStatus.REGISTERED, false, false, null, null));
        assertThat(SessionClosePolicy.shouldFinalize(ps, DEADLINE.minusSeconds(60), DEADLINE))
                .isFalse();
    }

    @Test
    @DisplayName("전원 REGISTERED(미출주)는 deadline 도달 시 전원 DNS로 최종화")
    void 전원_REGISTERED_전원_DNS() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.REGISTERED, false, false, null, null),
                close(2L, ParticipationStatus.REGISTERED, false, false, null, null));
        assertThat(SessionClosePolicy.shouldFinalize(ps, DEADLINE, DEADLINE)).isTrue();
        List<ParticipantOutcome> out = SessionClosePolicy.finalize(ps);
        assertThat(out).extracting(ParticipantOutcome::finalStatus)
                .containsExactly(ParticipationStatus.DNS, ParticipationStatus.DNS);
        assertThat(out).allSatisfy(o -> {
            assertThat(o.recordTimeS()).isNull();
            assertThat(o.totalDistanceM()).isNull();
        });
    }

    @Test
    @DisplayName("이미 최종 상태(FINISHED/DNF/DNS)인 참가자는 최종화에서 보존된다")
    void 이미_최종상태_보존() {
        List<ParticipantClose> ps = List.of(
                close(1L, ParticipationStatus.FINISHED, true, true, 1500, 5000),
                close(2L, ParticipationStatus.DNF, true, false, null, 3200),
                close(3L, ParticipationStatus.DNS, false, false, null, null));
        List<ParticipantOutcome> out = SessionClosePolicy.finalize(ps);
        assertThat(out).extracting(ParticipantOutcome::finalStatus).containsExactly(
                ParticipationStatus.FINISHED, ParticipationStatus.DNF, ParticipationStatus.DNS);
        assertThat(out.get(0).recordTimeS()).isEqualTo(1500);   // 완주 기록 보존
        assertThat(out.get(0).totalDistanceM()).isEqualTo(5000);
        assertThat(out.get(1).recordTimeS()).isNull();          // DNF는 기록 미성립
        assertThat(out.get(1).totalDistanceM()).isEqualTo(3200); // DNF도 뛴 거리 보존
    }
}
