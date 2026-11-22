package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.BackendApplication;
import com.doqsa.backend.adapter.out.ocr.openai.OpenAiOcrImageAnalyzer;
import com.doqsa.backend.adapter.out.ocr.grok.GrokOcrImageAnalyzer;
import com.doqsa.backend.adapter.out.ocr.local.DisabledOcrImageAnalyzer;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class OcrProviderSelectionTest {
    @ParameterizedTest @ValueSource(strings = {"none", "openai", "grok"})
    void bootsTheApplicationWithOnlyOneAnalyzerAndOnlyTheSelectedProvidersKey(String provider) {
        new ApplicationContextRunner().withUserConfiguration(BackendApplication.class)
                .withPropertyValues("doqsa.ocr.provider=" + provider, "spring.ai.model.chat=none",
                        "doqsa.ocr." + provider + ".api-key=test-key",
                        "spring.ai.model.embedding=none", "spring.ai.model.image=none", "spring.ai.model.moderation=none",
                        "spring.ai.model.audio.speech=none", "spring.ai.model.audio.transcription=none",
                        "spring.datasource.url=jdbc:h2:mem:ocr-provider-" + provider + ";DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
                        "doqsa.storage.provider=local", "doqsa.storage.local-directory=./build/test-images/ocr-" + provider,
                        "doqsa.queue.enabled=false", "doqsa.queue.polling-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(OcrImageAnalyzer.class);
                    var expected = switch (provider) {
                        case "openai" -> OpenAiOcrImageAnalyzer.class;
                        case "grok" -> GrokOcrImageAnalyzer.class;
                        default -> DisabledOcrImageAnalyzer.class;
                    };
                    assertThat(context.getBean(OcrImageAnalyzer.class)).isInstanceOf(expected);
                    if (provider.equals("none")) assertThat(context).doesNotHaveBean(ChatModel.class);
                    else assertThat(context).hasSingleBean(ChatModel.class).hasSingleBean(org.springframework.ai.openai.api.OpenAiApi.class);
                });
    }
}
