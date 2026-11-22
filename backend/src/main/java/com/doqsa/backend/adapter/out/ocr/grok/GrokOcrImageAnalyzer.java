package com.doqsa.backend.adapter.out.ocr.grok;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.application.service.OcrExceptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "doqsa.ocr.provider", havingValue = "grok")
public class GrokOcrImageAnalyzer implements OcrImageAnalyzer {
    private final ChatClient client;
    private final Resource prompt;
    public record Extracted(Receipt detected_info) { }

    public GrokOcrImageAnalyzer(@Qualifier("grokOcrChatClient") ChatClient client,
                                  @Value("classpath:prompts/receipt.txt") Resource prompt) {
        this.client = client;
        this.prompt = prompt;
    }

    @Override public Receipt analyze(byte[] image, String contentType) {
        try {
            var instructions = prompt.getContentAsString(StandardCharsets.UTF_8);
            var result = client.prompt().user(user -> user.text(instructions)
                    .media(Media.builder().mimeType(MimeTypeUtils.parseMimeType(contentType)).data(image).build()))
                    .call().entity(Extracted.class);
            if (result == null || result.detected_info() == null) throw new OcrExceptions.Retryable("Empty Grok OCR response");
            return result.detected_info();
        } catch (Exception exception) { throw new OcrExceptions.Retryable("Grok OCR failed", exception); }
    }
}
