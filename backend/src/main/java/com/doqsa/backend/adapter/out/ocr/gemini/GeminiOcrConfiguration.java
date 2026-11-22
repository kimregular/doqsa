package com.doqsa.backend.adapter.out.ocr.gemini;

import com.doqsa.backend.adapter.out.ocr.ReceiptExtraction;
import com.doqsa.backend.global.OcrProperties;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/**
 * Gemini OCR plugin. Active when {@code doqsa.ocr.provider=gemini}.
 * Spring AI chat auto-configuration is disabled globally, so this plugin wires its own model:
 * one HTTP attempt with the queue's external-call timeout, retries left to the OCR queue.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = GeminiOcrConfiguration.PROVIDER_PROPERTY, havingValue = GeminiOcrConfiguration.PROVIDER)
@EnableConfigurationProperties(GeminiOcrProperties.class)
public class GeminiOcrConfiguration {
    public static final String PROVIDER_PROPERTY = "doqsa.ocr.provider";
    public static final String PROVIDER = "gemini";

    @Bean(destroyMethod = "close")
    Client geminiClient(GeminiOcrProperties gemini, OcrProperties properties) {
        return Client.builder().apiKey(gemini.getApiKey()).httpOptions(HttpOptions.builder()
                .timeout(Math.toIntExact(properties.getExternalCallTimeout().toMillis()))
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build()).build();
    }

    @Bean GoogleGenAiChatModel geminiChatModel(Client client, GeminiOcrProperties gemini,
                                               ObjectProvider<ObservationRegistry> observations) {
        var options = new GoogleGenAiChatOptions();
        options.setModel(gemini.getModel());
        return GoogleGenAiChatModel.builder().genAiClient(client).defaultOptions(options)
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .observationRegistry(observations.getIfUnique(() -> ObservationRegistry.NOOP)).build();
    }

    @Bean GeminiOcrImageAnalyzer geminiOcrImageAnalyzer(GoogleGenAiChatModel model, ReceiptExtraction extraction) {
        return new GeminiOcrImageAnalyzer(model, extraction);
    }
}
