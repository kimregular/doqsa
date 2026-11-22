package com.doqsa.backend.application.service;

import com.doqsa.backend.application.model.OcrModels.*;
import com.doqsa.backend.application.port.in.OcrUseCase;
import com.doqsa.backend.application.port.out.*;
import com.doqsa.backend.domain.ocritem.*;
import com.doqsa.backend.global.OcrProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class OcrService implements OcrUseCase {
    private final OcrItemRepository repository;
    private final ImageStorage storage;
    private final OcrStateService states;
    private final OcrProperties properties;
    private final JsonMapper mapper;

    @Override @Transactional
    public Created create(String fileName, String contentType) {
        var upload = storage.issueUpload(fileName, contentType);
        var item = repository.save(OcrItem.create(fileName, contentType, upload.storageKey(),
                properties.getMaxAttempts(), repository.now()));
        return new Created(item.getId(), item.getStatus(), upload.url(), upload.expiresAt());
    }

    @Override
    public View uploaded(Long id) {
        var item = require(id);
        if (item.getStatus() == OcrStatus.WAITING_UPLOAD) {
            if (!storage.exists(item.getStorageKey())) throw new OcrExceptions.Conflict("Image has not been uploaded");
            states.uploaded(id);
        }
        return get(id);
    }

    @Override @Transactional(readOnly = true)
    public View get(Long id) { return view(require(id)); }

    @Override @Transactional(readOnly = true)
    public Page list(Long cursor, int size) {
        if (size < 1 || size > 100 || (cursor != null && cursor < 1)) throw new IllegalArgumentException("Invalid page");
        var fetched = repository.page(cursor, size + 1);
        boolean hasNext = fetched.size() > size;
        var items = hasNext ? fetched.subList(0, size) : fetched;
        return new Page(items.stream().map(this::view).toList(), hasNext ? items.getLast().getId() : null, hasNext);
    }

    @Override
    public Upload reissueUpload(Long id) {
        var item = require(id);
        if (item.getStatus() != OcrStatus.WAITING_UPLOAD) throw new OcrExceptions.Conflict("Upload already acknowledged");
        return storage.reissueUpload(item.getStorageKey(), item.getContentType());
    }

    @Override public void delete(Long id) { states.delete(id); }

    private OcrItem require(Long id) { return repository.find(id).orElseThrow(OcrExceptions.NotFound::new); }
    private View view(OcrItem item) {
        Receipt result = item.getResultJson() == null ? null : mapper.readValue(item.getResultJson(), Receipt.class);
        return new View(item.getId(), item.getFileName(), item.getStatus(), item.getAttempts(), item.getFailureCode(),
                item.getCreatedAt(), item.getCompletedAt(), storage.readUrl(item.getStorageKey()), result);
    }
}
