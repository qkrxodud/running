package com.runningcrew.tracking.application;

import com.runningcrew.tracking.application.view.TrackRecordSummary;

/**
 * 업로드 결과 — {@code created}=신규 수용(201) / false=동일 멱등 키 재요청(200, 기존 결과).
 */
public record UploadOutcome(TrackRecordSummary summary, boolean created) {
}
