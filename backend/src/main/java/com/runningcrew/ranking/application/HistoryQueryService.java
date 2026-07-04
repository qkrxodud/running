package com.runningcrew.ranking.application;

import com.runningcrew.ranking.application.port.out.LoadHistoryPort;
import com.runningcrew.ranking.application.view.HistoryRecordView;
import com.runningcrew.ranking.application.view.PersonalBestView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 기록 히스토리·PB 조회(history-api §1·§2). 본인 한정(토큰 sub). track_record 스캔 기반 — payload 격리(HS-2).
 */
@Service
public class HistoryQueryService {

    private final LoadHistoryPort loadHistoryPort;

    public HistoryQueryService(LoadHistoryPort loadHistoryPort) {
        this.loadHistoryPort = loadHistoryPort;
    }

    @Transactional(readOnly = true)
    public Page<HistoryRecordView> myRecords(Long userId, Pageable pageable) {
        return loadHistoryPort.findRecords(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<PersonalBestView> myPersonalBests(Long userId, Pageable pageable) {
        return loadHistoryPort.findPersonalBests(userId, pageable);
    }
}
