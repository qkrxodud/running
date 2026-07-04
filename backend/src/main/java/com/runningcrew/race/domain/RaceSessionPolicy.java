package com.runningcrew.race.domain;

/**
 * 세션 상태 전이 정책(설계 22 §2.4 합법/불법 매트릭스) — <b>순수 함수</b>(골든 대상, B2-T2 전수).
 *
 * <pre>
 * 명령\status  DRAFT  OPEN            RUNNING     FINALIZING  COMPLETED  CANCELLED
 * open         →OPEN  409             409         409         409        409
 * register     409    OPEN(불변)      409         409         409        409
 * start        409    →RUNNING(첫)    RUNNING(멱등) 409       409        409
 * cancel       →CANCELLED →CANCELLED  →CANCELLED  409(M2)     409        409
 * </pre>
 *
 * @return 명령 적용 후 세션 상태(register는 상태 불변이라 현 상태 그대로 반환).
 * @throws IllegalSessionTransitionException 불법 전이
 */
public final class RaceSessionPolicy {

    private RaceSessionPolicy() {
    }

    public static RaceStatus apply(RaceStatus current, SessionCommand command) {
        return switch (command) {
            case OPEN -> current == RaceStatus.DRAFT
                    ? RaceStatus.OPEN
                    : illegal(current, command);
            case REGISTER -> current == RaceStatus.OPEN
                    ? RaceStatus.OPEN
                    : illegal(current, command);
            case START -> (current == RaceStatus.OPEN || current == RaceStatus.RUNNING)
                    ? RaceStatus.RUNNING
                    : illegal(current, command);
            case CANCEL -> (current == RaceStatus.DRAFT || current == RaceStatus.OPEN
                    || current == RaceStatus.RUNNING)
                    ? RaceStatus.CANCELLED
                    : illegal(current, command);
        };
    }

    private static RaceStatus illegal(RaceStatus current, SessionCommand command) {
        throw new IllegalSessionTransitionException(current, command);
    }
}
