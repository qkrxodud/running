package com.runningcrew.replay.domain;

import com.runningcrew.tracking.domain.GpsGap;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackSegment;
import java.util.List;

/**
 * 리플레이 병합 입력(순수 도메인 — 프레임워크 무관). 한 참가자의 refined 트랙 재료. 어댑터가 refined_payload +
 * track_record에서 파싱해 조립한다(replay는 tracking.domain VO 재사용 — projection 계층, R-2 6컨텍스트 밖).
 *
 * @param userId           참가자
 * @param startedAtMillis  시작 버튼 시각(epoch ms) — t=0 기준(RP-4)
 * @param finishedAtMillis 완주 시각(epoch ms). DNF면 null
 * @param finishStatus     {@code FINISHED}|{@code DNF}
 * @param coords           refined 좌표열(원시 아님 — RP-4)
 * @param frameTimesMillis 각 좌표의 GPS 시각(epoch ms) — coords와 길이 동일
 * @param gaps             GPS 유실 구간(is_gap 표기)
 * @param segments         구간 페이스(500m, refined_payload 내장 — 색상 입력)
 */
public record ReplayTrackInput(
        long userId,
        long startedAtMillis,
        Long finishedAtMillis,
        String finishStatus,
        List<TrackCoord> coords,
        long[] frameTimesMillis,
        List<GpsGap> gaps,
        List<TrackSegment> segments) {
}
