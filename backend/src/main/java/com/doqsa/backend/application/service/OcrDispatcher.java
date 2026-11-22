package com.doqsa.backend.application.service;

import com.doqsa.backend.application.port.out.*;
import com.doqsa.backend.global.OcrProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component @Slf4j
public class OcrDispatcher implements SmartLifecycle {
    private final OcrItemRepository repository;
    private final OcrTaskExecutor executor;
    private final OcrProperties properties;
    private final Semaphore slots;
    private volatile boolean running;

    public OcrDispatcher(OcrItemRepository repository, OcrTaskExecutor executor, OcrProperties properties) {
        this.repository = repository;
        this.executor = executor;
        this.properties = properties;
        this.slots = new Semaphore(properties.getConcurrency());
    }

    public boolean dispatch(Long id) {
        if (!running || !slots.tryAcquire()) return false;
        try {
            executor.execute(id, slots::release);
            return true;
        } catch (RuntimeException exception) {
            slots.release();
            log.warn("OCR dispatch deferred: itemId={}", id, exception);
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${doqsa.queue.poll-delay-ms:1000}")
    public void poll() {
        if (!running || !properties.isPollingEnabled()) return;
        try {
            int capacity = slots.availablePermits();
            if (capacity > 0) for (var id : repository.runnable(capacity)) dispatch(id);
        } catch (RuntimeException exception) { log.error("OCR polling failed", exception); }
    }

    @Override public void start() { running = properties.isEnabled(); }
    @Override public void stop() {
        if (!running) return;
        running = false;
        try {
            boolean drained = slots.tryAcquire(properties.getConcurrency(), properties.getShutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (drained) slots.release(properties.getConcurrency());
            else log.warn("OCR shutdown deadline reached; unfinished jobs remain recoverable by lease expiry");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}
