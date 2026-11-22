package com.doqsa.backend.adapter.out.ocr;

import com.doqsa.backend.adapter.out.ocr.openai.*;
import com.doqsa.backend.adapter.out.ocr.grok.*;
import com.doqsa.backend.adapter.out.ocr.local.DisabledOcrConfiguration;
import com.doqsa.backend.adapter.out.ocr.local.DisabledOcrImageAnalyzer;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.service.OcrExceptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.json.JsonMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class OpenAiCompatibleOcrConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(ReceiptExtraction.class)
            .withUserConfiguration(OpenAiOcrConfiguration.class, GrokOcrConfiguration.class, DisabledOcrConfiguration.class);

    private ApplicationContextRunner selected(String provider) {
        return runner.withPropertyValues("doqsa.ocr.provider=" + provider, "doqsa.ocr." + provider + ".api-key=test-key");
    }

    @Test void disabledProviderRequiresNeitherApiKey() {
        runner.withPropertyValues("doqsa.ocr.provider=none").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OcrImageAnalyzer.class).doesNotHaveBean(ChatModel.class)
                    .doesNotHaveBean(OpenAiOcrProperties.class).doesNotHaveBean(GrokOcrProperties.class);
            assertThat(context.getBean(OcrImageAnalyzer.class)).isInstanceOf(DisabledOcrImageAnalyzer.class);
        });
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void selectsOnlyTheRequestedAnalyzerAndItsDefaults(String provider) {
        selected(provider).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OcrImageAnalyzer.class).hasSingleBean(ChatModel.class);
            var model = context.getBean(ChatModel.class);
            assertThat(model.getDefaultOptions().getModel()).isEqualTo(provider.equals("openai") ? "gpt-4o-mini" : "grok-2-vision-1212");
            if (provider.equals("openai")) {
                assertThat(context.getBean(OcrImageAnalyzer.class)).isInstanceOf(OpenAiOcrImageAnalyzer.class);
                assertThat(context.getBean(OpenAiOcrProperties.class).getBaseUrl().toString()).isEqualTo("https://api.openai.com/v1");
                assertThat(context).doesNotHaveBean(GrokOcrProperties.class);
            } else {
                assertThat(context.getBean(OcrImageAnalyzer.class)).isInstanceOf(GrokOcrImageAnalyzer.class);
                assertThat(context.getBean(GrokOcrProperties.class).getBaseUrl().toString()).isEqualTo("https://api.x.ai/v1");
                assertThat(context).doesNotHaveBean(OpenAiOcrProperties.class);
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void selectedProviderFailsFastWithoutItsKey(String provider) {
        runner.withPropertyValues("doqsa.ocr.provider=" + provider).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void rejectsInvalidEndpointsAndTimeouts(String provider) {
        selected(provider).withPropertyValues("doqsa.ocr." + provider + ".base-url=file:///tmp/api")
                .run(context -> assertThat(context).hasFailed());
        selected(provider).withPropertyValues("doqsa.ocr." + provider + ".timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void postsVisionInputToExactlyOneV1PathAndConvertsTheResponse(String provider) throws Exception {
        var path = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var calls = new AtomicInteger();
        var mapper = JsonMapper.builder().build();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            path.set(exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String extracted = """
                    {"detected_info":{"name":"test","address":null,"tell":null,"mobile":null,"memo":null}}
                    """;
            String response = """
                    {"id":"test","object":"chat.completion","created":1,"model":"vision-test",
                     "choices":[{"index":0,"message":{"role":"assistant","content":%s},"finish_reason":"stop"}]}
                    """.formatted(mapper.writeValueAsString(extracted));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            selected(provider).withPropertyValues(
                    "doqsa.ocr." + provider + ".base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "doqsa.ocr." + provider + ".model=vision-test").run(context -> {
                assertThat(context).hasNotFailed();
                byte[] image = {1, 2, 3};
                assertThat(context.getBean(OcrImageAnalyzer.class).analyze(image, "image/png"))
                        .isEqualTo(new Receipt("test", null, null, null, null));
                assertThat(path.get()).isEqualTo("/v1/chat/completions");
                assertThat(auth.get()).isEqualTo("Bearer test-key");
                var sent = mapper.readTree(body.get());
                assertThat(sent.get("model").asText()).isEqualTo("vision-test");
                var content = sent.get("messages").get(0).get("content");
                assertThat(content.get(0).get("text").asText()).contains("detected_info");
                assertThat(content.get(1).get("image_url").get("url").asText())
                        .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(image));
                assertThat(calls.get()).isEqualTo(1);
            });
        } finally { server.stop(0); }
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void leavesTransientHttpRetriesToThePersistentQueue(String provider) throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = """
                    {"error":{"message":"unavailable"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            selected(provider).withPropertyValues("doqsa.ocr." + provider + ".base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThatThrownBy(() -> context.getBean(OcrImageAnalyzer.class).analyze(new byte[]{1}, "image/png"))
                                .isInstanceOf(OcrExceptions.Retryable.class);
                        assertThat(calls.get()).isEqualTo(1);
                    });
        } finally { server.stop(0); }
    }

    @ParameterizedTest @ValueSource(strings = {"openai", "grok"})
    void enforcesTheConfiguredReadTimeout(String provider) throws Exception {
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try { release.await(5, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            selected(provider).withPropertyValues(
                    "doqsa.ocr." + provider + ".base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "doqsa.ocr." + provider + ".timeout=250ms").run(context -> {
                assertThat(context).hasNotFailed();
                long started = System.nanoTime();
                assertThatThrownBy(() -> context.getBean(OcrImageAnalyzer.class).analyze(new byte[]{1}, "image/png"))
                        .isInstanceOf(OcrExceptions.Retryable.class);
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(3));
                assertThat(calls.get()).isEqualTo(1);
            });
        } finally { release.countDown(); server.stop(0); }
    }

}
