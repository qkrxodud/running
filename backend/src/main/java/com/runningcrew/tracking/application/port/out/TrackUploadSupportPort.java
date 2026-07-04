package com.runningcrew.tracking.application.port.out;

import com.runningcrew.tracking.domain.CourseShape;
import java.util.Optional;

/**
 * 업로드 수용 판단에 필요한 타 컨텍스트 정보 out-port — race_session·participation·course·race_result를
 * <b>네이티브 SQL</b>로만 조회한다(race 컨텍스트 클래스 미참조 — ArchUnit R-2). 블롭 미접근.
 */
public interface TrackUploadSupportPort {

    /** 세션 상태 문자열(DRAFT/OPEN/…). 세션 없으면 empty. */
    Optional<String> findSessionStatus(Long sessionId);

    /**
     * 세션 소유 크루의 ACTIVE 멤버 여부(R-007 권한 경계). 비멤버면 403 — 세션 존재·상태 누설 금지
     * (Crew invite-only 규범). participation(409)보다 <b>앞서</b> 평가한다(404→403→409).
     */
    boolean isActiveCrewMember(Long sessionId, Long userId);

    /** (session,user) participation 행 존재 여부(선 register). */
    boolean participationExists(Long sessionId, Long userId);

    /** 세션의 코스 형상(폴리라인·finish·distance) — FinishPolicy 입력. 코스/세션 없으면 empty. */
    Optional<CourseShape> findCourseShape(Long sessionId);

    /** 결과 확정(race_result 존재) 여부 — 확정 후 업로드 거부. */
    boolean resultExists(Long sessionId);
}
