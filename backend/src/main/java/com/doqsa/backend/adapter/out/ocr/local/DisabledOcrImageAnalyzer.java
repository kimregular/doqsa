package com.doqsa.backend.adapter.out.ocr.local;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;

/** Placeholder for {@code doqsa.ocr.provider=none}: every attempt fails permanently with {@code OCR_FAILED}. */
public class DisabledOcrImageAnalyzer implements OcrImageAnalyzer {
    @Override public Receipt analyze(byte[] image, String contentType) {
        throw new IllegalStateException("OCR is disabled. Set doqsa.ocr.provider (OCR_PROVIDER) to gemini, openai, grok or local");
    }
}
