package com.runningcrew.race.application.port.out;

import com.runningcrew.race.application.view.CourseDetailView;
import com.runningcrew.race.application.view.CourseSummaryView;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 코스 조회(읽기 모델) out-port. */
public interface CourseQueryPort {

    Optional<CourseDetailView> findDetail(Long courseId);

    Page<CourseSummaryView> findByCrew(Long crewId, Pageable pageable);

    /** 코스의 소유 크루 id(세션 생성 시 소유 검증·상세 조회 멤버십 판정용). 없으면 empty. */
    Optional<Long> findCrewId(Long courseId);
}
