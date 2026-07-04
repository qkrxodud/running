package com.runningcrew.race.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.race.application.port.out.PromotionSourcePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * {@link PromotionSourcePort} 구현 — 승격의 <b>명시적 payload 소비자</b>(PR-6). track_record(요약)와
 * track_payload(refined 블롭)를 네이티브 SQL로 조인해 소유자·완주여부·거리·refined 폴리라인을 얻는다.
 * finish_status는 파생값(finished_at 존재=FINISHED). tracking 클래스 미참조(R-2). 조회 어댑터엔 미주입.
 */
@Repository
public class PromotionSourceAdapter implements PromotionSourcePort {

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper;

    public PromotionSourceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PromotionSource> find(Long trackRecordId) {
        List<?> rows = em.createNativeQuery(
                        "SELECT tr.user_id, tr.finished_at, tr.total_distance_m, tp.refined_payload "
                                + "FROM track_record tr "
                                + "JOIN track_payload tp ON tp.track_record_id = tr.id "
                                + "WHERE tr.id = ?1")
                .setParameter(1, trackRecordId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = (Object[]) rows.get(0);
        Long ownerUserId = ((Number) r[0]).longValue();
        boolean finished = r[1] != null;
        int totalDistanceM = r[2] != null ? ((Number) r[2]).intValue() : 0;
        String refinedPolyline = extractPolyline((String) r[3]);
        return Optional.of(new PromotionSource(ownerUserId, finished, totalDistanceM, refinedPolyline));
    }

    /** refined_payload JSON의 {@code polyline}(1e5) 추출. 부재·파싱 실패 시 null(→ 승격 불가 판정). */
    private String extractPolyline(String refinedPayloadJson) {
        if (refinedPayloadJson == null || refinedPayloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(refinedPayloadJson).get("polyline");
            return node != null && node.isTextual() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
