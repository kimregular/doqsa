package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.adapter.out.ocr.gemini.*;
import com.doqsa.backend.adapter.out.ocr.grok.*;
import com.doqsa.backend.adapter.out.ocr.local.*;
import com.doqsa.backend.adapter.out.ocr.openai.*;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.global.OcrProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class OcrPluginSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(OcrProperties.class)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(ReceiptExtraction.class)
            .withUserConfiguration(DisabledOcrConfiguration.class, LocalOcrConfiguration.class, GeminiOcrConfiguration.class,
                    OpenAiOcrConfiguration.class, GrokOcrConfiguration.class);

    @Test void ocrIsDisabledWhenNoProviderIsConfigured() {
        runner.run(context -> assertThat(context).hasSingleBean(OcrImageAnalyzer.class)
                .getBean(OcrImageAnalyzer.class).isInstanceOf(DisabledOcrImageAnalyzer.class));
    }
    @Test void noneSelectsTheDisabledPlugin() {
        runner.withPropertyValues("doqsa.ocr.provider=none").run(context -> {
            assertThat(context).hasSingleBean(OcrImageAnalyzer.class).doesNotHaveBean(GoogleGenAiChatModel.class);
            var analyzer = context.getBean(OcrImageAnalyzer.class);
            assertThat(analyzer).isInstanceOf(DisabledOcrImageAnalyzer.class);
            assertThatThrownBy(() -> analyzer.analyze(new byte[]{1}, "image/png")).isInstanceOf(IllegalStateException.class);
        });
    }
    @Test void localReturnsTheBundledSampleWithoutCallingAModel() {
        runner.withPropertyValues("doqsa.ocr.provider=local").run(context -> {
            assertThat(context).hasSingleBean(OcrImageAnalyzer.class).doesNotHaveBean(GoogleGenAiChatModel.class);
            var analyzer = context.getBean(OcrImageAnalyzer.class);
            assertThat(analyzer).isInstanceOf(LocalOcrImageAnalyzer.class);
            assertThat(analyzer.analyze(new byte[]{1}, "image/png").name()).isEqualTo("로컬 샘플");
        });
    }
    @Test void localIsNeverActiveInProd() {
        runner.withPropertyValues("doqsa.ocr.provider=local", "spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(OcrImageAnalyzer.class));
    }
    @Test void geminiWiresItsOwnModelFromPluginProperties() {
        runner.withPropertyValues("doqsa.ocr.provider=gemini", "doqsa.ocr.gemini.api-key=test-key",
                "doqsa.ocr.gemini.model=gemini-test").run(context -> {
            assertThat(context).hasSingleBean(OcrImageAnalyzer.class)
                    .getBean(OcrImageAnalyzer.class).isInstanceOf(GeminiOcrImageAnalyzer.class);
            assertThat(context.getBean(GoogleGenAiChatModel.class).getDefaultOptions().getModel()).isEqualTo("gemini-test");
        });
    }
    @Test void geminiRefusesToStartWithoutAnApiKey() {
        runner.withPropertyValues("doqsa.ocr.provider=gemini").run(context -> assertThat(context).hasFailed());
    }
    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void openAiCompatibleProvidersWireOnlyTheirOwnModel(String provider) {
        runner.withPropertyValues("doqsa.ocr.provider=" + provider, "doqsa.ocr." + provider + ".api-key=test-key")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OcrImageAnalyzer.class)
                            .hasSingleBean(OpenAiChatModel.class).doesNotHaveBean(GoogleGenAiChatModel.class);
                    assertThat(context.getBean(OcrImageAnalyzer.class)).isInstanceOf(
                            provider.equals("openai") ? OpenAiOcrImageAnalyzer.class : GrokOcrImageAnalyzer.class);
                });
    }
    @Test void unknownProviderRegistersNoAnalyzer() {
        runner.withPropertyValues("doqsa.ocr.provider=unknown")
                .run(context -> assertThat(context).doesNotHaveBean(OcrImageAnalyzer.class));
    }
}
