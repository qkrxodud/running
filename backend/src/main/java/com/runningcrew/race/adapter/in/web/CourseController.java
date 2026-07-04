package com.runningcrew.race.adapter.in.web;

import com.runningcrew.common.web.AuthUserId;
import com.runningcrew.common.web.PageResponse;
import com.runningcrew.race.adapter.in.web.dto.CourseDetailResponse;
import com.runningcrew.race.adapter.in.web.dto.CourseSummaryResponse;
import com.runningcrew.race.adapter.in.web.dto.CreateCourseRequest;
import com.runningcrew.race.application.CourseCommandService;
import com.runningcrew.race.application.CourseQueryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 코스 API(계약 course-api.md) — 전부 인증 필요. */
@RestController
@RequestMapping("/api/v1")
public class CourseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CourseCommandService commandService;
    private final CourseQueryService queryService;

    public CourseController(CourseCommandService commandService, CourseQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping("/crews/{crewId}/courses")
    public ResponseEntity<CourseDetailResponse> createCourse(
            @AuthUserId Long userId,
            @PathVariable Long crewId,
            @Valid @RequestBody CreateCourseRequest request) {
        CourseDetailResponse body = CourseDetailResponse.from(
                commandService.createCourse(userId, crewId, request.toCommand()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/crews/{crewId}/courses")
    public PageResponse<CourseSummaryResponse> listCourses(
            @AuthUserId Long userId,
            @PathVariable Long crewId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int clampedPage = Math.max(page, 0);
        return PageResponse.from(queryService
                .listCourses(userId, crewId, PageRequest.of(clampedPage, clampedSize))
                .map(CourseSummaryResponse::from));
    }

    @GetMapping("/courses/{courseId}")
    public CourseDetailResponse getCourse(@AuthUserId Long userId, @PathVariable Long courseId) {
        return CourseDetailResponse.from(queryService.getCourse(userId, courseId));
    }
}
