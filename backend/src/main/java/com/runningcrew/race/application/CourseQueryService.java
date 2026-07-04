package com.runningcrew.race.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.race.application.port.out.CourseQueryPort;
import com.runningcrew.race.application.port.out.CrewAccessPort;
import com.runningcrew.race.application.view.CourseDetailView;
import com.runningcrew.race.application.view.CourseSummaryView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 조회 유스케이스(course-api.md §2·§3). 코스는 크루 소유 — ACTIVE 멤버만 조회.
 */
@Service
public class CourseQueryService {

    private final CourseQueryPort courseQueryPort;
    private final CrewAccessPort crewAccessPort;

    public CourseQueryService(CourseQueryPort courseQueryPort, CrewAccessPort crewAccessPort) {
        this.courseQueryPort = courseQueryPort;
        this.crewAccessPort = crewAccessPort;
    }

    @Transactional(readOnly = true)
    public Page<CourseSummaryView> listCourses(Long userId, Long crewId, Pageable pageable) {
        if (!crewAccessPort.isActiveMember(crewId, userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        return courseQueryPort.findByCrew(crewId, pageable);
    }

    @Transactional(readOnly = true)
    public CourseDetailView getCourse(Long userId, Long courseId) {
        CourseDetailView view = courseQueryPort.findDetail(courseId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crewAccessPort.isActiveMember(view.crewId(), userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 비멤버 조회(코스는 크루 소유)
        }
        return view;
    }
}
