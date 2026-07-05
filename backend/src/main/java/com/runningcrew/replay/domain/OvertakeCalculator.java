package com.runningcrew.replay.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 추월 사전계산(순수 함수 3종 中 A2). 두 참가자의 "진행거리 d 도달 상대시각" T(d)를 비교해 부호 반전(교차)
 * 지점을 추월로 기록한다. IO·시계·랜덤 0(골든 대상, RP-5).
 *
 * <p>정의(설계 §3.2): sign(T_A(d) − T_B(d))가 d 증가에 따라 뒤집히면 추월. 경계:
 * <ul>
 *   <li><b>동시 도달</b>(T_A=T_B): 부호 0 — 이벤트 아님(명확한 반전만 추월).
 *   <li><b>재역전</b>: 반전마다 별도 이벤트(d 오름차순 순서대로 N건).
 *   <li><b>DNF 조기 종료</b>: 각자 도달 최대거리까지만 T(d) 정의 → 공통 범위 밖 d는 비교 제외(미발생).
 *   <li>진행거리 범위 미교집합: 공통 도달 거리 없음 → 이벤트 없음.
 * </ul>
 * 결정성: overtakes는 (at_dist_m 오름차순, passer_user_id 오름차순, passed_user_id 오름차순) 정렬.
 * v1 노이즈 필터 없음 — 부호 반전 전량 기록(설계 §9).
 */
public final class OvertakeCalculator {

    private OvertakeCalculator() {
    }

    public static List<Overtake> computeOvertakes(List<ReplayParticipant> participants) {
        List<Overtake> result = new ArrayList<>();
        int m = participants.size();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                collectPairOvertakes(participants.get(i), participants.get(j), result);
            }
        }
        result.sort((a, b) -> {
            if (a.atDistM() != b.atDistM()) {
                return Integer.compare(a.atDistM(), b.atDistM());
            }
            if (a.passerUserId() != b.passerUserId()) {
                return Long.compare(a.passerUserId(), b.passerUserId());
            }
            return Long.compare(a.passedUserId(), b.passedUserId());
        });
        return result;
    }

    private static void collectPairOvertakes(ReplayParticipant a, ReplayParticipant b,
                                             List<Overtake> out) {
        if (a.frames().isEmpty() || b.frames().isEmpty()) {
            return;
        }
        int maxA = a.frames().get(a.frames().size() - 1).cumDistM();
        int maxB = b.frames().get(b.frames().size() - 1).cumDistM();
        int commonMax = Math.min(maxA, maxB);
        if (commonMax <= 0) {
            return;   // 공통 도달 거리 없음
        }
        // 평가 지점: 두 참가자 프레임의 cum_dist 합집합 중 [0, commonMax] (결정적 오름차순).
        TreeSet<Integer> breakpoints = new TreeSet<>();
        breakpoints.add(0);
        breakpoints.add(commonMax);
        for (ReplayFrame f : a.frames()) {
            if (f.cumDistM() > 0 && f.cumDistM() <= commonMax) {
                breakpoints.add(f.cumDistM());
            }
        }
        for (ReplayFrame f : b.frames()) {
            if (f.cumDistM() > 0 && f.cumDistM() <= commonMax) {
                breakpoints.add(f.cumDistM());
            }
        }

        Integer prevD = null;
        int prevSign = 0;              // 직전 평가점의 부호(0 포함)
        int lastNonZeroSign = 0;       // 직전 비영 부호(교차 기준)
        long prevDelta = 0;
        for (int d : breakpoints) {
            long ta = timeAtDistance(a, d);
            long tb = timeAtDistance(b, d);
            long delta = ta - tb;                 // >0: A가 d에 늦게 도달(뒤) / <0: A가 앞
            int sign = Long.signum(delta);
            if (prevD != null && sign != 0 && lastNonZeroSign != 0 && sign != lastNonZeroSign) {
                // 부호 반전 = 추월. 교차 거리 선형 보간(prevD..d 사이 delta=0 지점).
                int crossD = interpolateZeroCrossing(prevD, prevDelta, d, delta);
                long tAcross = timeAtDistance(a, crossD);
                long tBcross = timeAtDistance(b, crossD);
                long tMs = Math.max(tAcross, tBcross);   // 둘 다 통과 = 추월 완료
                if (lastNonZeroSign > 0) {
                    // A가 뒤였다가(Δ>0) 앞으로(Δ<0) → A가 B를 추월
                    out.add(new Overtake(crossD, a.userId(), b.userId(), tMs));
                } else {
                    out.add(new Overtake(crossD, b.userId(), a.userId(), tMs));
                }
            }
            if (sign != 0) {
                lastNonZeroSign = sign;
            }
            prevD = d;
            prevSign = sign;
            prevDelta = delta;
        }
    }

    /** 선형 보간으로 delta가 0이 되는 거리(prevD..d). 정수 반올림. */
    private static int interpolateZeroCrossing(int prevD, long prevDelta, int d, long delta) {
        long span = delta - prevDelta;
        if (span == 0) {
            return d;
        }
        double f = (double) (-prevDelta) / (double) span;   // prevDelta + f*(delta-prevDelta)=0
        double crossD = prevD + f * (d - prevD);
        int rounded = (int) Math.round(crossD);
        if (rounded < prevD) {
            return prevD;
        }
        if (rounded > d) {
            return d;
        }
        return rounded;
    }

    /**
     * 참가자 u가 누적거리 target에 도달한 상대시각(ms). frames의 cum_dist_m 선형보간(비내림차순 전제).
     * target ≤ 0이면 첫 프레임, target ≥ 최대면 마지막 프레임.
     */
    private static long timeAtDistance(ReplayParticipant u, int target) {
        List<ReplayFrame> frames = u.frames();
        ReplayFrame first = frames.get(0);
        if (target <= first.cumDistM()) {
            return first.tMs();
        }
        ReplayFrame last = frames.get(frames.size() - 1);
        if (target >= last.cumDistM()) {
            return last.tMs();
        }
        for (int i = 1; i < frames.size(); i++) {
            ReplayFrame cur = frames.get(i);
            if (target <= cur.cumDistM()) {
                ReplayFrame prev = frames.get(i - 1);
                int span = cur.cumDistM() - prev.cumDistM();
                if (span <= 0) {
                    return cur.tMs();
                }
                double frac = (double) (target - prev.cumDistM()) / span;
                return Math.round(prev.tMs() + (cur.tMs() - prev.tMs()) * frac);
            }
        }
        return last.tMs();
    }
}
