package com.runningcrew.tracking.application.port.out;

/**
 * 위치 원본(track_payload) 파기 out-port(설계 §1.3 step5). 탈퇴 시 해당 유저의 트랙 원본만 삭제하고
 * track_record 요약 메타·순위 파생물은 보존한다(익명 보존 원칙).
 */
public interface TrackDataEraser {

    /** 해당 유저의 모든 track_record에 딸린 track_payload 삭제. B1 시점 데이터가 없어도 경로는 존재. */
    void eraseByUserId(Long userId);
}
