package com.runningcrew.crew.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 크루장 승계 선정 순수 함수(설계 12 §3.3, 불변식 C-B2).
 *
 * <p>규칙: 남은 ACTIVE 멤버 중 <b>joined_at 최소</b>(최선임)를 승계. 동률이면 <b>멤버십 id 오름차순</b>
 * (tie-break 확정). 재가입 멤버는 joined_at이 재참가 시각으로 갱신되므로 서열이 자연히 뒤로 간다.
 *
 * <p>IO·시계·랜덤 없는 순수 함수 — 골든/유닛 테스트 대상(test-engineer 이관).
 */
public final class LeaderSuccessionPolicy {

    private LeaderSuccessionPolicy() {
    }

    /**
     * 승계자 선정. 인자는 <b>탈퇴자를 제외한</b> 멤버 후보군 — ACTIVE만 후보로 필터한다.
     * 후보가 없으면 {@link Optional#empty()}(호출측이 크루 CLOSED 처리).
     *
     * <p>id 비교의 안정성을 위해 id가 null(미영속)인 후보는 뒤로 정렬한다(정상 흐름에선 영속 멤버만 옴).
     */
    public static Optional<CrewMember> selectSuccessor(List<CrewMember> candidates) {
        return candidates.stream()
                .filter(CrewMember::isActive)
                .min(Comparator
                        .comparing(CrewMember::getJoinedAt)
                        .thenComparing(m -> m.getId() == null ? Long.MAX_VALUE : m.getId()));
    }
}
