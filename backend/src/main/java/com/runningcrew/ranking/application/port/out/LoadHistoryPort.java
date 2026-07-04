package com.runningcrew.ranking.application.port.out;

import com.runningcrew.ranking.application.view.HistoryRecordView;
import com.runningcrew.ranking.application.view.PersonalBestView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 내 기록 히스토리·PB 조회 out-port(history-api §1·§2). <b>track_record 스캔 + rank_entry/race_session
 * 조인</b>으로만 조회한다 — <b>track_payload(블롭) 조인 0건</b>(HS-2, QA payload 격리 3차 재검증 대상).
 * 본인(userId) 한정. race/user 클래스 미참조(R-2 — 네이티브 SQL). `rank` 예약어 백틱 인용(R-003 이월5).
 */
public interface LoadHistoryPort {

    /** 본인 실주행 기록(FINISHED+DNF, CANCELLED 배지 포함) 최신순(scheduled_at DESC) 페이징. */
    Page<HistoryRecordView> findRecords(Long userId, Pageable pageable);

    /** 본인 코스별 최고 완주 기록(유저×course_id 최소 record_time_s, 확정 세션 FINISHED만). */
    Page<PersonalBestView> findPersonalBests(Long userId, Pageable pageable);
}
