package com.doqsa.backend.adapter.out.ocr.openai;

import com.doqsa.backend.adapter.out.ocr.ReceiptExtraction;
import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.application.service.OcrExceptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.util.MimeTypeUtils;

/** OpenAI OCR through the OpenAI-compatible Chat Completions API; registered by {@link OpenAiOcrConfiguration}. */
public class OpenAiOcrImageAnalyzer implements OcrImageAnalyzer {
    private final ChatClient client;
    private final ReceiptExtraction extraction;

    public OpenAiOcrImageAnalyzer(ChatModel model, ReceiptExtraction extraction) {
        this.client = ChatClient.builder(model).build();
        this.extraction = extraction;
    }

    @Override public Receipt analyze(byte[] image, String contentType) {
        try {
            var result = client.prompt().user(user -> user.text(extraction.instructions())
                    .media(Media.builder().mimeType(MimeTypeUtils.parseMimeType(contentType)).data(image).build()))
                    .call().entity(ReceiptExtraction.Extracted.class);
            return extraction.unwrap(result);
        } catch (Exception exception) { throw new OcrExceptions.Retryable("OpenAI OCR failed", exception); }
    }
}
