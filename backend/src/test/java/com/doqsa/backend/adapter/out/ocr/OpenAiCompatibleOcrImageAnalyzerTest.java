package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.adapter.out.ocr.openai.OpenAiOcrImageAnalyzer;
import com.doqsa.backend.adapter.out.ocr.grok.GrokOcrImageAnalyzer;
import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.application.service.OcrExceptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiCompatibleOcrImageAnalyzerTest {
    private static final byte[] IMAGE = {1, 2, 3};

    private OcrImageAnalyzer analyzer(String provider, ChatModel model) {
        var client = ChatClient.builder(model).build();
        var prompt = new ClassPathResource("prompts/receipt.txt");
        return provider.equals("openai") ? new OpenAiOcrImageAnalyzer(client, prompt) : new GrokOcrImageAnalyzer(client, prompt);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void extractsReceiptAndSendsTheOriginalImageWithTheReceiptPrompt(String provider) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"detected_info":{"name":"홍길동","address":"서울시","tell":"02-***-4567","mobile":null,"memo":"문 앞"}}
                """));
        assertThat(analyzer(provider, model).analyze(IMAGE, "image/png"))
                .isEqualTo(new Receipt("홍길동", "서울시", "02-***-4567", null, "문 앞"));
        var sent = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(sent.capture());
        var message = (UserMessage) sent.getValue().getInstructions().getFirst();
        assertThat(message.getText()).contains("detected_info", "Do not guess", "customer/delivery");
        assertThat(message.getMedia()).hasSize(1);
        assertThat(message.getMedia().getFirst().getMimeType().toString()).isEqualTo("image/png");
        assertThat((byte[]) message.getMedia().getFirst().getData()).containsExactly(IMAGE);
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void preservesUnknownReceiptFieldsAsNull(String provider) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"detected_info":{"name":null,"address":null,"tell":null,"mobile":null,"memo":null}}
                """));
        assertThat(analyzer(provider, model).analyze(IMAGE, "image/jpeg"))
                .isEqualTo(new Receipt(null, null, null, null, null));
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void rejectsAnEmptyModelResponse(String provider) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
        assertThatThrownBy(() -> analyzer(provider, model).analyze(IMAGE, "image/png"))
                .isInstanceOf(OcrExceptions.Retryable.class);
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void rejectsAnEnvelopeWithoutReceiptData(String provider) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("""
                {"detected_info":null}
                """));
        assertThatThrownBy(() -> analyzer(provider, model).analyze(IMAGE, "image/png"))
                .isInstanceOf(OcrExceptions.Retryable.class);
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void rejectsMalformedStructuredOutput(String provider) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(response("not JSON"));
        assertThatThrownBy(() -> analyzer(provider, model).analyze(IMAGE, "image/png"))
                .isInstanceOf(OcrExceptions.Retryable.class);
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void wrapsProviderFailuresWithoutExposingTheirMessage(String provider) {
        var model = mock(ChatModel.class);
        var failure = new IllegalStateException("sensitive upstream message");
        when(model.call(any(Prompt.class))).thenThrow(failure);
        assertThatThrownBy(() -> analyzer(provider, model).analyze(IMAGE, "image/png"))
                .isInstanceOf(OcrExceptions.Retryable.class).hasCause(failure)
                .hasMessageNotContaining("sensitive upstream message");
        verify(model, times(1)).call(any(Prompt.class));
    }
}
