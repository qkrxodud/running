package com.runningcrew.replay.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.runningcrew.replay.application.port.out.ReplaySourcePort.ParticipantSource;
import com.runningcrew.replay.domain.ReplayTrackInput;
import com.runningcrew.tracking.domain.GpsGap;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackPolylineCodec;
import com.runningcrew.tracking.domain.TrackSegment;
import java.util.ArrayList;
import java.util.List;

/**
 * refined_payload(JSON) → {@link ReplayTrackInput} 파서. tracking이 저장한 refined 스키마(polyline·timestamps·
 * gps_gaps·segments)를 병합 입력으로 변환한다(RP-4: refined 좌표 — 원시 아님). 순수 변환(IO 없음, 예외는 호출자).
 */
final class RefinedTrackParser {

    private final ObjectMapper objectMapper;

    RefinedTrackParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ReplayTrackInput parse(ParticipantSource p) throws Exception {
        JsonNode root = objectMapper.readTree(p.refinedPayloadJson());
        List<TrackCoord> coords = TrackPolylineCodec.decode(root.get("polyline").asText());

        JsonNode tsNode = root.get("timestamps");
        long[] times = new long[tsNode.size()];
        for (int i = 0; i < tsNode.size(); i++) {
            times[i] = tsNode.get(i).asLong();
        }
        if (times.length != coords.size()) {
            throw new IllegalStateException("refined polyline/timestamps 길이 불일치: "
                    + coords.size() + " vs " + times.length);
        }

        List<GpsGap> gaps = new ArrayList<>();
        JsonNode gapsNode = root.get("gps_gaps");
        if (gapsNode != null) {
            for (JsonNode g : gapsNode) {
                gaps.add(new GpsGap(g.get("start_index").asInt(), g.get("end_index").asInt(),
                        g.get("delta_ms").asLong()));
            }
        }

        List<TrackSegment> segments = new ArrayList<>();
        JsonNode segsNode = root.get("segments");
        if (segsNode != null) {
            for (JsonNode s : segsNode) {
                segments.add(new TrackSegment(s.get("index").asInt(),
                        s.get("start_distance_m").asInt(), s.get("end_distance_m").asInt(),
                        s.get("duration_s").asLong(), s.get("avg_pace_s_per_km").asInt()));
            }
        }

        return new ReplayTrackInput(p.userId(), p.startedAtMillis(), p.finishedAtMillis(),
                p.finishStatus(), coords, times, gaps, segments);
    }
}
