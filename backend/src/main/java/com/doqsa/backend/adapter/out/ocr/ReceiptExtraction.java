package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.service.OcrExceptions;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Receipt prompt and structured-response contract shared by every OCR plugin.
 * The prompt is loaded once at startup, so a missing prompt fails fast instead of failing each OCR attempt.
 */
@Component
public class ReceiptExtraction {
    public static final String PROMPT_LOCATION = "prompts/receipt.txt";

    /** Model response shape requested by the receipt prompt. */
    public record Extracted(Receipt detected_info) { }

    private final String instructions;
    private final JsonMapper mapper;

    public ReceiptExtraction(JsonMapper mapper) {
        this.mapper = mapper;
        try {
            instructions = new ClassPathResource(PROMPT_LOCATION).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("OCR prompt not found: " + PROMPT_LOCATION, exception);
        }
    }

    public String instructions() { return instructions; }

    /** Returns the receipt, treating an empty model answer as a retryable failure. */
    public Receipt unwrap(Extracted extracted) {
        if (extracted == null || extracted.detected_info() == null) throw new OcrExceptions.Retryable("Empty OCR response");
        return extracted.detected_info();
    }

    /** Parses a raw JSON answer in the prompt's {@code {"detected_info": {...}}} format. */
    public Receipt parse(String json) {
        try {
            return unwrap(mapper.readValue(json, Extracted.class));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("OCR response is not valid receipt JSON", exception);
        }
    }
}
