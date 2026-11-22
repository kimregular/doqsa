package com.doqsa.backend;

import com.doqsa.backend.adapter.out.persistence.OcrItemJpaRepository;
import com.doqsa.backend.adapter.out.storage.local.LocalImageStorage;
import com.doqsa.backend.application.model.OcrModels.Receipt;
import com.doqsa.backend.application.port.in.OcrUseCase;
import com.doqsa.backend.application.port.out.OcrImageAnalyzer;
import com.doqsa.backend.application.service.*;
import com.doqsa.backend.domain.ocritem.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:async-test;DB_CLOSE_DELAY=-1",
        "doqsa.queue.enabled=true", "doqsa.queue.polling-enabled=true", "doqsa.queue.poll-delay-ms=100",
        "doqsa.queue.concurrency=1", "doqsa.queue.lease-duration=3s", "doqsa.queue.heartbeat-interval=100ms",
        "doqsa.queue.task-timeout=5s", "doqsa.queue.external-call-timeout=1s",
        "doqsa.storage.local-directory=./build/test-images/async"
})
class OcrAsyncTest {
    @Autowired OcrUseCase ocr;
    @Autowired OcrStateService states;
    @Autowired OcrItemJpaRepository jpa;
    @Autowired LocalImageStorage storage;
    @Autowired OcrDispatcher dispatcher;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OcrImageAnalyzer analyzer;

    private Long uploaded(boolean acknowledge) throws Exception {
        var created = ocr.create("image.png", "image/png");
        storage.upload(created.uploadUrl().substring(created.uploadUrl().lastIndexOf('/') + 1), new ByteArrayInputStream(new byte[]{1}));
        if (acknowledge) ocr.uploaded(created.id());
        return created.id();
    }
    private void completed(Long id) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(ocr.get(id).status()).isEqualTo(OcrStatus.COMPLETED));
    }
    @BeforeEach void setup() {
        dispatcher.start();
        jpa.deleteAll();
        when(analyzer.analyze(any(), anyString())).thenReturn(new Receipt("test", null, null, null, null));
    }

    @Test void springAsyncRunsAfterUploadCommit() throws Exception {
        var thread = new AtomicReference<String>();
        when(analyzer.analyze(any(), anyString())).thenAnswer(invocation -> {
            thread.set(Thread.currentThread().getName());
            return new Receipt("async", null, null, null, null);
        });
        var id = uploaded(true);
        completed(id);
        assertThat(thread.get()).startsWith("ocr-worker-");
    }

    @Test void fullWorkerDefersUnclaimedWorkAndHeartbeatsKeepItsLeaseAlive() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(analyzer.analyze(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            return new Receipt("test", null, null, null, null);
        });
        Long first = null, next = null;
        try {
            first = uploaded(true);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var initialLease = jpa.findById(first).orElseThrow().getLeaseExpiresAt();
            next = uploaded(true);
            assertThat(ocr.get(next).status()).isEqualTo(OcrStatus.QUEUED);
            assertThat(jpa.findById(next).orElseThrow().getClaimToken()).isNull();
            Long runningId = first;
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(jpa.findById(runningId).orElseThrow().getLeaseExpiresAt()).isAfter(initialLease));
        } finally { release.countDown(); }
        completed(first);
        completed(next);
    }

    @Test void pollingFindsWorkWithoutAnEventAndReclaimsAnExpiredWorker() throws Exception {
        var id = uploaded(false);
        transactions.executeWithoutResult(tx -> {
            var item = jpa.lock(id).orElseThrow();
            item.queueUploaded();
            item.claim("crashed-worker", "old-token", jpa.databaseNow(), Duration.ofSeconds(30));
        });
        jdbc.update("update ocr_item set lease_expires_at = ? where id = ?", jpa.databaseNow().minusSeconds(1), id);
        completed(id);
        assertThat(ocr.get(id).attempts()).isEqualTo(2);
    }

    @Test void rollbackDoesNotDispatchAndStoppedDispatcherLeavesWorkForRecovery() throws Exception {
        var id = uploaded(false);
        transactions.executeWithoutResult(tx -> { states.uploaded(id); tx.setRollbackOnly(); });
        assertThat(ocr.get(id).status()).isEqualTo(OcrStatus.WAITING_UPLOAD);
        verifyNoInteractions(analyzer);
        dispatcher.stop();
        try {
            ocr.uploaded(id);
            assertThat(dispatcher.dispatch(id)).isFalse();
            assertThat(ocr.get(id).attempts()).isZero();
        } finally { dispatcher.start(); }
        completed(id);
    }

    @Test void shutdownStopsNewClaimsAndWaitsForTheCurrentWorker() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(analyzer.analyze(any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            return new Receipt("finished during shutdown", null, null, null, null);
        });
        var stop = new FutureTask<Void>(() -> { dispatcher.stop(); return null; });
        Long id = null;
        try {
            id = uploaded(true);
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            Thread.ofVirtual().start(stop);
            await().atMost(Duration.ofSeconds(2)).until(() -> !dispatcher.isRunning());
            assertThat(stop.isDone()).isFalse();
            var pending = uploaded(true);
            assertThat(dispatcher.dispatch(pending)).isFalse();
            assertThat(ocr.get(pending).attempts()).isZero();
            // Remove pending work before restarting the dispatcher in this isolated test.
            ocr.delete(pending);
        } finally {
            release.countDown();
            stop.get(5, TimeUnit.SECONDS);
            dispatcher.start();
        }
        completed(id);
    }
}
