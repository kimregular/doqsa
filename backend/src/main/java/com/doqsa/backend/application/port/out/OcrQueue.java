package com.doqsa.backend.application.port.out;

/** Publishes a wake-up signal. Persisted job state is the source of truth. */
public interface OcrQueue {
    void enqueue(Long itemId);
}
