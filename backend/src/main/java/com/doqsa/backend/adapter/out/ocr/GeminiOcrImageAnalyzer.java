package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.application.service.OcrExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import java.nio.charset.StandardCharsets;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GeminiOcrImageAnalyzer implements OcrImageAnalyzer {
    private final ChatClient client;
    @Value("classpath:prompts/receipt.txt") private Resource prompt;
    public record Extracted(Receipt detected_info) { }

    @Override public Receipt analyze(byte[] image, String contentType) {
        try {
            var instructions = prompt.getContentAsString(StandardCharsets.UTF_8);
            var result = client.prompt().user(user -> user.text(instructions)
                    .media(Media.builder().mimeType(MimeTypeUtils.parseMimeType(contentType)).data(image).build()))
                    .call().entity(Extracted.class);
            if (result == null || result.detected_info() == null) throw new OcrExceptions.Retryable("Empty OCR response");
            return result.detected_info();
        } catch (Exception exception) { throw new OcrExceptions.Retryable("Gemini OCR failed", exception); }
    }
}
