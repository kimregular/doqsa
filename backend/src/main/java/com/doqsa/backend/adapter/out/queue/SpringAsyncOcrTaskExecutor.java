package com.doqsa.backend.adapter.out.queue;

import com.doqsa.backend.application.port.out.OcrTaskExecutor;
import com.doqsa.backend.application.service.OcrProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SpringAsyncOcrTaskExecutor implements OcrTaskExecutor {
    private final OcrProcessor processor;

    @Async("ocrExecutor")
    @Override public void execute(Long itemId, Runnable onCompletion) {
        try { processor.process(itemId); } finally { onCompletion.run(); }
    }
}
