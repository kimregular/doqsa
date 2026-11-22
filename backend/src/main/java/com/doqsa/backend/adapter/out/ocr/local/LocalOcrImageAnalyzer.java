package com.doqsa.backend.adapter.out.ocr.local;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;

/**
 * Offline OCR for development: returns a fixed receipt without calling any model, so the full
 * upload → queue → COMPLETED flow runs without API keys. Registered by {@link LocalOcrConfiguration}.
 */
public class LocalOcrImageAnalyzer implements OcrImageAnalyzer {
    private final Receipt response;

    public LocalOcrImageAnalyzer(Receipt response) { this.response = response; }

    @Override public Receipt analyze(byte[] image, String contentType) {
        if (image == null || image.length == 0) throw new IllegalArgumentException("Image is empty");
        return response;
    }
}
