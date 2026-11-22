package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "none", matchIfMissing = true)
public class DisabledOcrImageAnalyzer implements OcrImageAnalyzer {
    @Override public Receipt analyze(byte[] image, String contentType) {
        throw new IllegalStateException("Configure OCR_PROVIDER=google-genai and GOOGLE_GEMINI_API_KEY to run OCR");
    }
}
