package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.global.OcrProperties;
import com.google.genai.Client;
import com.google.genai.types.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.retry.*;

@Configuration
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class GeminiConfiguration {
    @Bean(destroyMethod = "close")
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey, OcrProperties properties) {
        return Client.builder().apiKey(apiKey).httpOptions(HttpOptions.builder()
                .timeout(Math.toIntExact(properties.getExternalCallTimeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
    }
    @Bean RetryTemplate ocrModelRetryTemplate() { return new RetryTemplate(RetryPolicy.withMaxRetries(0)); }
    @Bean ChatClient chatClient(ChatModel model) { return ChatClient.builder(model).build(); }
}
