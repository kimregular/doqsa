package com.doqsa.backend.application.port.out;

import com.doqsa.backend.application.model.OcrModels.Receipt;

public interface OcrImageAnalyzer {
    Receipt analyze(byte[] image, String contentType);
}
