package com.runningcrew.crew.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LeaderSuccessionPolicy#selectSuccessor} 순수 승계 선정 골든 테스트 (배치 B1).
 *
 * <p>기대값 근거: crew-api.md v0.2 §Enum(크루장 자동 승계 — 가입일 최선임, 동률은 id 오름차순),
 * domain-model 스킬 Crew 불변식(크루장 항상 1명·가입일 최선 승계), 13_backend_report_B1.md §3.2.
 * 상태전이(승계 TX·CLOSED)는 backend-dev 통합 테스트가 커버 — 여기선 순수 선정 함수의 경계만.
 */
class LeaderSuccessionPolicyTest {

    private static final Instant T1 = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-02T00:00:00Z");

    private static CrewMember active(Long id, Instant joinedAt) {
        return new CrewMember(id, 100L + id, CrewRole.MEMBER, joinedAt, CrewMemberStatus.ACTIVE);
    }

    private static CrewMember withdrawn(Long id, Instant joinedAt) {
        return new CrewMember(id, 100L + id, CrewRole.MEMBER, joinedAt, CrewMemberStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("가입일이 더 이른 멤버가 승계자로 선정된다(최선임)")
    void 가입일_최선임이_승계() {
        List<CrewMember> candidates = List.of(active(2L, T2), active(3L, T1));

        Optional<CrewMember> successor = LeaderSuccessionPolicy.selectSuccessor(candidates);

        assertThat(successor).map(CrewMember::getId).contains(3L);
    }

    @Test
    @DisplayName("가입일이 동률이면 멤버십 id가 작은 쪽이 승계자로 선정된다(tie-break)")
    void 가입일_동률이면_id_오름차순() {
        List<CrewMember> candidates = List.of(active(5L, T1), active(4L, T1));

        Optional<CrewMember> successor = LeaderSuccessionPolicy.selectSuccessor(candidates);

        assertThat(successor).map(CrewMember::getId).contains(4L);
    }

    @Test
    @DisplayName("가입일이 더 이르더라도 WITHDRAWN 멤버는 후보에서 제외된다")
    void WITHDRAWN은_가입일_이르러도_제외() {
        // id1이 가장 이른 가입(T1)이지만 WITHDRAWN → ACTIVE인 id2(T2)가 승계.
        List<CrewMember> candidates = List.of(active(2L, T2), withdrawn(1L, T1));

        Optional<CrewMember> successor = LeaderSuccessionPolicy.selectSuccessor(candidates);

        assertThat(successor).map(CrewMember::getId).contains(2L);
    }

    @Test
    @DisplayName("ACTIVE 후보가 단 1명이면 그 멤버가 승계자다")
    void 단일_ACTIVE_후보() {
        List<CrewMember> candidates = List.of(active(9L, T2));

        Optional<CrewMember> successor = LeaderSuccessionPolicy.selectSuccessor(candidates);

        assertThat(successor).map(CrewMember::getId).contains(9L);
    }

    @Test
    @DisplayName("후보 리스트가 비어 있으면 승계자 없음(→ 호출측 CLOSED)")
    void 빈_후보는_empty() {
        assertThat(LeaderSuccessionPolicy.selectSuccessor(List.of())).isEmpty();
    }

    @Test
    @DisplayName("후보가 전원 WITHDRAWN이면 승계자 없음(→ 호출측 CLOSED)")
    void 전원_WITHDRAWN은_empty() {
        List<CrewMember> candidates = List.of(withdrawn(1L, T1), withdrawn(2L, T2));

        assertThat(LeaderSuccessionPolicy.selectSuccessor(candidates)).isEmpty();
    }

    @Test
    @DisplayName("가입일·id 동시 동률 방지: 이른 가입일과 작은 id가 각각 독립적으로 우선순위를 결정한다")
    void 가입일_우선_그다음_id() {
        // id가 가장 크지만(7) 가입일이 가장 이른(T1) 멤버가, id가 작은(2) 늦은 가입(T2)보다 우선.
        List<CrewMember> candidates = List.of(active(2L, T2), active(7L, T1));

        Optional<CrewMember> successor = LeaderSuccessionPolicy.selectSuccessor(candidates);

        assertThat(successor).map(CrewMember::getId).contains(7L);
    }
}
