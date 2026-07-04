package com.runningcrew.ranking.adapter.out.persistence;

import com.runningcrew.ranking.application.port.out.LoadHistoryPort;
import com.runningcrew.ranking.application.view.HistoryRecordView;
import com.runningcrew.ranking.application.view.PersonalBestView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * {@link LoadHistoryPort} 구현 — 네이티브 SQL. <b>track_record 스캔 + race_session/course/race_result/
 * rank_entry 조인</b>이며 <b>track_payload는 조인하지 않는다</b>(블롭 격리 — HS-2, QA 3차 재검증). race/user
 * 클래스 미참조(R-2). `rank`·`user`는 MySQL 예약어라 백틱 인용(R-003 이월5). 본인(userId) 한정.
 */
@Repository
public class HistoryQueryAdapter implements LoadHistoryPort {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<HistoryRecordView> findRecords(Long userId, Pageable pageable) {
        long total = ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM track_record WHERE user_id = ?1")
                .setParameter(1, userId)
                .getSingleResult()).longValue();

        List<?> rows = em.createNativeQuery(
                        "SELECT tr.id, tr.session_id, rs.course_id, c.name, rs.scheduled_at, "
                                + "  tr.finished_at, re.`rank`, tr.total_time_s, tr.total_distance_m, "
                                + "  COALESCE(re.is_pb, FALSE), rs.status "
                                + "FROM track_record tr "
                                + "JOIN race_session rs ON rs.id = tr.session_id "
                                + "JOIN course c ON c.id = rs.course_id "
                                + "LEFT JOIN race_result rr ON rr.session_id = tr.session_id "
                                + "LEFT JOIN rank_entry re ON re.result_id = rr.id "
                                + "  AND re.user_id = tr.user_id "
                                + "WHERE tr.user_id = ?1 "
                                + "ORDER BY rs.scheduled_at DESC, tr.id DESC")
                .setParameter(1, userId)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        List<HistoryRecordView> items = new ArrayList<>(rows.size());
        for (Object o : rows) {
            Object[] r = (Object[]) o;
            Long trackRecordId = ((Number) r[0]).longValue();
            Long sessionId = ((Number) r[1]).longValue();
            Long courseId = ((Number) r[2]).longValue();
            String courseName = (String) r[3];
            Instant scheduledAt = toInstant(r[4]);
            boolean finished = r[5] != null;                                   // finished_at 존재 = FINISHED
            String finishStatus = finished ? "FINISHED" : "DNF";
            Integer rank = r[6] != null ? ((Number) r[6]).intValue() : null;
            Integer recordTimeS = finished && r[7] != null ? ((Number) r[7]).intValue() : null;
            Integer totalDistanceM = r[8] != null ? ((Number) r[8]).intValue() : null;
            Integer avgPace = avgPace(recordTimeS, totalDistanceM);
            boolean isPb = toBool(r[9]);
            boolean sessionCancelled = "CANCELLED".equals(r[10]);
            items.add(new HistoryRecordView(trackRecordId, sessionId, courseId, courseName,
                    scheduledAt, finishStatus, rank, recordTimeS, totalDistanceM, avgPace, isPb,
                    sessionCancelled));
        }
        return new PageImpl<>(items, pageable, total);
    }

    @Override
    public Page<PersonalBestView> findPersonalBests(Long userId, Pageable pageable) {
        // 확정 세션 rank_entry의 완주(record_time_s 비null)만 — CANCELLED·DNF 제외(rank_entry 부재/null).
        // 코스별 최소 기록 순으로 정렬 → Java에서 코스별 첫 행(=최소, 동률은 scheduled_at 빠른 세션) 채택.
        List<?> rows = em.createNativeQuery(
                        "SELECT rs.course_id, c.name, c.distance_m, re.record_time_s, "
                                + "  rs.id, rs.scheduled_at "
                                + "FROM rank_entry re "
                                + "JOIN race_result rr ON rr.id = re.result_id "
                                + "JOIN race_session rs ON rs.id = rr.session_id "
                                + "JOIN course c ON c.id = rs.course_id "
                                + "WHERE re.user_id = ?1 AND re.record_time_s IS NOT NULL "
                                + "ORDER BY rs.course_id ASC, re.record_time_s ASC, rs.scheduled_at ASC")
                .setParameter(1, userId)
                .getResultList();

        Map<Long, PersonalBestView> bestByCourse = new LinkedHashMap<>();
        for (Object o : rows) {
            Object[] r = (Object[]) o;
            Long courseId = ((Number) r[0]).longValue();
            if (bestByCourse.containsKey(courseId)) {
                continue;   // 코스별 첫 행이 최소(정렬 보장)
            }
            String courseName = (String) r[1];
            int distanceM = ((Number) r[2]).intValue();
            int bestRecordTimeS = ((Number) r[3]).intValue();
            Long achievedSessionId = ((Number) r[4]).longValue();
            Instant achievedAt = toInstant(r[5]);
            int avgPace = avgPaceOrZero(bestRecordTimeS, distanceM);
            bestByCourse.put(courseId, new PersonalBestView(courseId, courseName, distanceM,
                    bestRecordTimeS, avgPace, achievedSessionId, achievedAt));
        }

        List<PersonalBestView> all = new ArrayList<>(bestByCourse.values());
        int from = Math.min((int) pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    private static Integer avgPace(Integer recordTimeS, Integer distanceM) {
        if (recordTimeS == null || distanceM == null || distanceM <= 0) {
            return null;
        }
        return (int) Math.round(recordTimeS / (distanceM / 1000.0));
    }

    private static int avgPaceOrZero(int recordTimeS, int distanceM) {
        return distanceM > 0 ? (int) Math.round(recordTimeS / (distanceM / 1000.0)) : 0;
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        return ((java.time.OffsetDateTime) o).toInstant();
    }

    private static boolean toBool(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        return o instanceof Number n && n.intValue() != 0;
    }
}
