package com.doqsa.backend.adapter.out.persistence;

import com.doqsa.backend.application.port.out.OcrItemRepository;
import com.doqsa.backend.domain.ocritem.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;

@Repository @RequiredArgsConstructor
public class OcrItemPersistenceAdapter implements OcrItemRepository {
    private final OcrItemJpaRepository jpa;
    @Override public OcrItem save(OcrItem item) { return jpa.save(item); }
    @Override public Optional<OcrItem> find(Long id) { return jpa.findByIdAndDeletedFalse(id); }
    @Override public Optional<OcrItem> lock(Long id) { return jpa.lock(id); }
    @Override public List<OcrItem> page(Long cursor, int size) { return jpa.page(cursor == null ? Long.MAX_VALUE : cursor, PageRequest.of(0, size)); }
    @Override public List<Long> runnable(int limit) { return jpa.runnable(OcrStatus.QUEUED, OcrStatus.PROCESSING, PageRequest.of(0, limit)); }
    @Override public LocalDateTime now() { return jpa.databaseNow(); }
}
