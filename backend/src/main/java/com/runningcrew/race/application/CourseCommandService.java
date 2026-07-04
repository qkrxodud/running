package com.runningcrew.race.application;

import com.runningcrew.common.error.ApiException;
import com.runningcrew.common.error.ErrorCode;
import com.runningcrew.race.application.port.out.CourseQueryPort;
import com.runningcrew.race.application.port.out.CourseRepository;
import com.runningcrew.race.application.port.out.CrewAccessPort;
import com.runningcrew.race.application.port.out.CrewAccessPort.CrewRef;
import com.runningcrew.race.application.view.CourseDetailView;
import com.runningcrew.race.domain.Course;
import com.runningcrew.race.domain.InvalidCourseException;
import com.runningcrew.race.domain.LatLng;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 명령 유스케이스(course-api.md §1). 크루장 전용 생성 — distance_m는 서버가 폴리라인에서 확정.
 */
@Service
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final CourseQueryPort courseQueryPort;
    private final CrewAccessPort crewAccessPort;
    private final Clock clock;

    public CourseCommandService(CourseRepository courseRepository,
                                CourseQueryPort courseQueryPort,
                                CrewAccessPort crewAccessPort,
                                Clock clock) {
        this.courseRepository = courseRepository;
        this.courseQueryPort = courseQueryPort;
        this.crewAccessPort = crewAccessPort;
        this.clock = clock;
    }

    /** 크루장 전용 코스 생성. 201 CourseDetail. */
    @Transactional
    public CourseDetailView createCourse(Long userId, Long crewId, CreateCourseCommand command) {
        CrewRef crew = crewAccessPort.findCrew(crewId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!crew.isLeader(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN);   // 크루장 아님(CO-B4)
        }
        if (crew.closed()) {
            throw new ApiException(ErrorCode.CREW_CLOSED);
        }
        Course course;
        try {
            course = Course.create(crewId, command.name(), command.routePolyline(),
                    new LatLng(command.startLat(), command.startLng()),
                    new LatLng(command.finishLat(), command.finishLng()),
                    userId, clock.instant());
        } catch (InvalidCourseException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
        Course saved = courseRepository.save(course);
        return courseQueryPort.findDetail(saved.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
