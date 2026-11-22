package com.doqsa.backend.domain.ocritem;

public enum OcrStatus {
    WAITING_UPLOAD, UPLOADED, QUEUED, PROCESSING, COMPLETED, FAILED;

    public boolean terminal() { return this == COMPLETED || this == FAILED; }
}
