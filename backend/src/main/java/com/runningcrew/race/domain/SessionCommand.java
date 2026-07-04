package com.runningcrew.race.domain;

/**
 * 세션 상태머신을 구동하는 명령(설계 22 §2.4 매트릭스의 행). 권한·멤버십 검증은 애플리케이션 소관이고,
 * 여기서는 순수하게 <b>상태 전이 합법성</b>만 판정한다({@link RaceSessionPolicy}).
 */
public enum SessionCommand {
    OPEN,
    REGISTER,
    START,
    CANCEL,
    // M2-A 마감·확정 전이(설계 42 §5.3 이벤트 흐름).
    FINALIZE,   // OPEN|RUNNING|FINALIZING → FINALIZING (전원 업로드 또는 deadline. 재진입 멱등)
    COMPLETE    // FINALIZING → COMPLETED (ResultFinalized 후)
}
