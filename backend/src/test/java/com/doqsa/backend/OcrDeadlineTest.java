package com.doqsa.backend;

import com.doqsa.backend.adapter.out.storage.LocalImageStorage;
import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.in.OcrUseCase;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.domain.ocritem.OcrStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deadline-test;DB_CLOSE_DELAY=-1",
        "doqsa.queue.enabled=true", "doqsa.queue.polling-enabled=true", "doqsa.queue.poll-delay-ms=50",
        "doqsa.queue.concurrency=1", "doqsa.queue.max-attempts=1", "doqsa.queue.lease-duration=2s",
        "doqsa.queue.heartbeat-interval=50ms", "doqsa.queue.task-timeout=500ms", "doqsa.queue.external-call-timeout=100ms",
        "doqsa.storage.local-directory=./build/test-images/deadline"
})
class OcrDeadlineTest {
    @Autowired OcrUseCase ocr;
    @Autowired LocalImageStorage storage;
    @MockitoBean OcrImageAnalyzer analyzer;

    @Test void timedOutTaskCannotSaveItsLateResult() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        when(analyzer.analyze(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timed out");
                return new Receipt("late result", null, null, null, null);
            } finally { returned.countDown(); }
        });
        var created = ocr.create("receipt.png", "image/png");
        storage.upload(created.uploadUrl().substring(created.uploadUrl().lastIndexOf('/') + 1), new ByteArrayInputStream(new byte[]{1}));
        try {
            ocr.uploaded(created.id());
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(ocr.get(created.id()).status()).isEqualTo(OcrStatus.FAILED));
        } finally { release.countDown(); }
        assertThat(returned.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(ocr.get(created.id()).result()).isNull();
        assertThat(ocr.get(created.id()).failureCode()).isEqualTo("RETRY_EXHAUSTED");
    }
}
