package com.doqsa.backend.application.service;

import com.doqsa.backend.application.model.OcrModels.Claim;
import com.doqsa.backend.global.OcrProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component @Slf4j
public class OcrLeaseMonitor {
    private final OcrStateService states;
    private final OcrProperties properties;
    private final ScheduledThreadPoolExecutor scheduler;

    public OcrLeaseMonitor(OcrStateService states, OcrProperties properties) {
        this.states = states;
        this.properties = properties;
        scheduler = new ScheduledThreadPoolExecutor(properties.getConcurrency(), Thread.ofPlatform().name("ocr-lease-", 0).factory());
        scheduler.setRemoveOnCancelPolicy(true);
    }

    public Lease watch(Claim claim) {
        var lease = new Lease(claim);
        lease.future = scheduler.scheduleWithFixedDelay(lease::tick, properties.getHeartbeatInterval().toMillis(),
                properties.getHeartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
        return lease;
    }

    @PreDestroy public void close() { scheduler.shutdownNow(); }

    public final class Lease implements AutoCloseable {
        private final Claim claim;
        private final long started = System.nanoTime();
        private final AtomicBoolean stopped = new AtomicBoolean();
        private ScheduledFuture<?> future;
        private Lease(Claim claim) { this.claim = claim; }
        public boolean active() { return !stopped.get() && System.nanoTime() - started < properties.getTaskTimeout().toNanos(); }
        private void tick() {
            if (stopped.get()) return;
            try {
                if (!active()) {
                    close();
                    states.fail(claim, true);
                } else if (!states.renew(claim, this::active)) close();
            } catch (RuntimeException exception) {
                log.warn("OCR heartbeat failed: itemId={}", claim.id(), exception);
                // Completion always checks the DB lease again; a heartbeat error never grants authority.
            }
        }
        @Override public void close() {
            stopped.set(true);
            if (future != null) future.cancel(false);
        }
    }
}
