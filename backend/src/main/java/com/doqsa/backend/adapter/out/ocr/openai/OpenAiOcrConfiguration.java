package com.doqsa.backend.adapter.out.ocr.openai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "doqsa.ocr.provider", havingValue = "openai")
@EnableConfigurationProperties(OpenAiOcrProperties.class)
public class OpenAiOcrConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient openAiOcrHttpClient(OpenAiOcrProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
    }

    @Bean
    OpenAiApi openAiOcrApi(OpenAiOcrProperties properties,
                          @Qualifier("openAiOcrHttpClient") HttpClient http) {
        var requests = new JdkClientHttpRequestFactory(http);
        requests.setReadTimeout(properties.getTimeout());
        return OpenAiApi.builder().apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl().toString())
                // baseUrl already contains /v1; avoid the SDK default /v1/chat/completions.
                .completionsPath("/chat/completions")
                .restClientBuilder(RestClient.builder().requestFactory(requests)).build();
    }

    @Bean
    OpenAiChatModel openAiOcrChatModel(OpenAiOcrProperties properties,
                                      @Qualifier("openAiOcrApi") OpenAiApi api) {
        return OpenAiChatModel.builder().openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model(properties.getModel()).temperature(0.0).build())
                // The persistent OCR queue owns retries and the attempt limit.
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
    }

    @Bean
    ChatClient openAiOcrChatClient(@Qualifier("openAiOcrChatModel") OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }
}
