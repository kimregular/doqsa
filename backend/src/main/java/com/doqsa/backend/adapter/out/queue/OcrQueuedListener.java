package com.doqsa.backend.adapter.out.queue;

import com.doqsa.backend.application.service.OcrDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component @RequiredArgsConstructor
public class OcrQueuedListener {
    private final OcrDispatcher dispatcher;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(SpringAsyncOcrQueue.Queued event) { dispatcher.dispatch(event.itemId()); }
}
