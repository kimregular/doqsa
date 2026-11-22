package com.doqsa.backend.adapter.out.queue;

import com.doqsa.backend.application.port.out.OcrQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SpringAsyncOcrQueue implements OcrQueue {
    private final ApplicationEventPublisher publisher;
    public record Queued(Long itemId) { }

    @Override public void enqueue(Long itemId) { publisher.publishEvent(new Queued(itemId)); }
}
