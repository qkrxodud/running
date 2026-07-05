package com.runningcrew.replay.domain;

import com.runningcrew.tracking.domain.GpsGap;
import com.runningcrew.tracking.domain.TrackCoord;
import com.runningcrew.tracking.domain.TrackGeo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 상대시각 병합(순수 함수 3종 中 A1). 각 참가자의 refined 좌표열을 <b>각자 시작 t=0 상대 시각</b>으로 정렬해
 * 프레임(누적거리·is_gap)과 색상 구간을 만든다. IO·시계·랜덤 0(골든 대상, RP-4).
 *
 * <p>t_ms = gps_time − started_at(각자). 시작 시각 상이해도 t=0 정렬로 "동시 출발 고스트" 병합(계획서 §5.6).
 * DNF도 frames 보존(finish_time_ms=null, RP-6). cum_dist는 refined 좌표 하버사인 누적(원시 금지, RP-4).
 */
public final class ReplayMerger {

    private ReplayMerger() {
    }

    public static MergedTimeline mergeToRelativeTimeline(List<ReplayTrackInput> tracks,
                                                         ColorParams colorParams) {
        List<ReplayParticipant> participants = new ArrayList<>(tracks.size());
        long durationMs = 0L;

        for (ReplayTrackInput t : tracks) {
            List<TrackCoord> coords = t.coords();
            int n = coords.size();
            // is_gap: 각 GpsGap의 endIndex 프레임(공백 직후 도착점)에 표기 — 뷰어 보간 구분.
            Set<Integer> gapEndIndexes = new HashSet<>();
            for (GpsGap g : t.gaps()) {
                gapEndIndexes.add(g.endIndex());
            }

            List<ReplayFrame> frames = new ArrayList<>(n);
            double cum = 0.0;
            long lastTMs = 0L;
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    cum += TrackGeo.haversineMeters(coords.get(i - 1), coords.get(i));
                }
                long tMs = t.frameTimesMillis()[i] - t.startedAtMillis();
                frames.add(new ReplayFrame(tMs, coords.get(i).lat(), coords.get(i).lng(),
                        (int) Math.round(cum), gapEndIndexes.contains(i)));
                lastTMs = tMs;
            }

            Long finishTimeMs = t.finishedAtMillis() != null
                    ? t.finishedAtMillis() - t.startedAtMillis()
                    : null;
            // 참가자 종료 시각: 완주=finish_time_ms, DNF=마지막 프레임 t_ms(트랙 끝).
            long endMs = finishTimeMs != null ? finishTimeMs : lastTMs;
            durationMs = Math.max(durationMs, endMs);

            List<ReplaySegmentColor> segments = PaceColorizer.colorize(t.segments(), colorParams);
            participants.add(new ReplayParticipant(t.userId(), t.finishStatus(), finishTimeMs,
                    frames, segments));
        }
        return new MergedTimeline(participants, durationMs);
    }
}
