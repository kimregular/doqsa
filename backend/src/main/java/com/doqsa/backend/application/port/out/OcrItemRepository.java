package com.doqsa.backend.application.port.out;

import com.doqsa.backend.domain.ocritem.OcrItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OcrItemRepository {
    OcrItem save(OcrItem item);
    Optional<OcrItem> find(Long id);
    Optional<OcrItem> lock(Long id);
    List<OcrItem> page(Long cursor, int size);
    List<Long> runnable(int limit);
    LocalDateTime now();
}
