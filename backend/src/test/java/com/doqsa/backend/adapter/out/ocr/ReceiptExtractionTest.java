package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.application.service.OcrExceptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ReceiptExtractionTest {
    private final ReceiptExtraction extraction = new ReceiptExtraction(JsonMapper.builder().build());

    @Test void loadsTheReceiptPromptOnce() {
        assertThat(extraction.instructions()).contains("detected_info");
    }
    @Test void parsesThePromptResponseFormat() {
        var receipt = extraction.parse("""
                {"detected_info":{"name":"홍길동","address":null,"tell":"02-123-4567","mobile":null,"memo":"문 앞"}}""");
        assertThat(receipt.name()).isEqualTo("홍길동");
        assertThat(receipt.tell()).isEqualTo("02-123-4567");
        assertThat(receipt.address()).isNull();
    }
    @Test void emptyAnswerIsRetryableAndMalformedJsonIsRejected() {
        assertThatThrownBy(() -> extraction.parse("{}")).isInstanceOf(OcrExceptions.Retryable.class);
        assertThatThrownBy(() -> extraction.unwrap(null)).isInstanceOf(OcrExceptions.Retryable.class);
        assertThatThrownBy(() -> extraction.parse("not json")).isInstanceOf(IllegalArgumentException.class);
    }
}
