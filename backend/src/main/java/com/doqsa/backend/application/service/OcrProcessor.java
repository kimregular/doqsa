package com.doqsa.backend.application.service;

import com.doqsa.backend.application.port.out.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor @Slf4j
public class OcrProcessor {
    private final OcrStateService states;
    private final OcrLeaseMonitor monitor;
    private final ImageStorage storage;
    private final OcrImageAnalyzer analyzer;

    public void process(Long id) {
        var optional = states.claim(id);
        if (optional.isEmpty()) return;
        var claim = optional.get();
        try (var lease = monitor.watch(claim)) {
            try {
                var image = storage.read(claim.storageKey());
                if (!lease.active()) throw new OcrExceptions.Retryable("OCR deadline exceeded");
                var result = analyzer.analyze(image, claim.contentType());
                if (result == null) throw new OcrExceptions.Retryable("Empty OCR response");
                if (!states.complete(claim, result, lease::active) && !lease.active()) states.fail(claim, true);
            } catch (RuntimeException exception) {
                log.warn("OCR attempt failed: itemId={}", id, exception);
                states.fail(claim, exception instanceof OcrExceptions.Retryable);
            }
        } catch (RuntimeException exception) {
            log.error("OCR attempt requires lease recovery: itemId={}", id, exception);
        }
    }
}
