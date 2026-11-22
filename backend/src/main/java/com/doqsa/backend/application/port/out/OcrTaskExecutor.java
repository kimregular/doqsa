package com.doqsa.backend.application.port.out;

public interface OcrTaskExecutor {
    void execute(Long itemId, Runnable onCompletion);
}
