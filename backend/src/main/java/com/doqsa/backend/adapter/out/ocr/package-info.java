/**
 * OCR model plugins implementing {@link com.doqsa.backend.application.port.out.OcrImageAnalyzer}.
 *
 * <p>Exactly one plugin is active, chosen by {@code doqsa.ocr.provider} ({@code OCR_PROVIDER}):
 * <ul>
 *   <li>{@code none} (default): {@code local.DisabledOcrConfiguration}, every attempt ends in {@code OCR_FAILED}</li>
 *   <li>{@code local}: {@code local.LocalOcrConfiguration}, fixed offline response, never in {@code prod}</li>
 *   <li>{@code gemini}: {@code gemini.GeminiOcrConfiguration}</li>
 *   <li>{@code openai}, {@code grok}: reserved names; no plugin yet, so the application refuses to start</li>
 * </ul>
 *
 * <p>Plugin contract, so that adding or removing a model touches nothing outside its package:
 * <ul>
 *   <li>One {@code @Configuration} annotated with
 *       {@code @ConditionalOnProperty(name = "doqsa.ocr.provider", havingValue = "<provider>")}
 *       declares every bean of the plugin, including exactly one {@code OcrImageAnalyzer}.
 *       Implementation classes carry no stereotype annotations.</li>
 *   <li>Spring AI chat auto-configuration is off ({@code spring.ai.model.chat=none}); a plugin that uses
 *       Spring AI builds its own {@code ChatModel} so plugins never compete for shared Spring AI beans.</li>
 *   <li>Use {@link com.doqsa.backend.adapter.out.ocr.ReceiptExtraction} for the prompt and response format.</li>
 *   <li>Throw {@link com.doqsa.backend.application.service.OcrExceptions.Retryable} for failures a later attempt
 *       may fix; any other exception fails the item permanently.</li>
 *   <li>Settings live under {@code doqsa.ocr.<provider>.*}.</li>
 * </ul>
 */
package com.doqsa.backend.adapter.out.ocr;
