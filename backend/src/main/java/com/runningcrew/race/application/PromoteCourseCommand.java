package com.runningcrew.race.application;

/** 코스 승격 입력(course-api §4). 소스는 본인 FINISHED track_record. distance·좌표는 서버가 refined에서 확정. */
public record PromoteCourseCommand(Long sourceTrackRecordId, String name) {
}
