package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "doqsa.ocr.provider", havingValue = "none", matchIfMissing = true)
public class DisabledOcrImageAnalyzer implements OcrImageAnalyzer {
    @Override public Receipt analyze(byte[] image, String contentType) {
        throw new IllegalStateException("Configure doqsa.ocr.provider and the selected provider API key to run OCR");
    }
}
