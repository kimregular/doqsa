package com.doqsa.backend.application.service;

import com.doqsa.backend.application.model.OcrModels.*;
import com.doqsa.backend.application.port.out.*;
import com.doqsa.backend.domain.ocritem.OcrItem;
import com.doqsa.backend.global.OcrProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

@Service
@RequiredArgsConstructor
public class OcrStateService {
    private final OcrItemRepository repository;
    private final OcrQueue queue;
    private final OcrProperties properties;
    private final JsonMapper mapper;

    @Transactional
    public void uploaded(Long id) {
        var item = requireLocked(id);
        if (item.queueUploaded()) queue.enqueue(id);
    }

    @Transactional
    public Optional<Claim> claim(Long id) {
        var item = repository.lock(id).orElse(null);
        if (item == null || !item.claim(properties.getOwner(), UUID.randomUUID().toString(),
                repository.now(), properties.getLeaseDuration())) return Optional.empty();
        return Optional.of(Claim.from(item));
    }

    @Transactional
    public boolean renew(Claim claim, BooleanSupplier active) {
        var item = repository.lock(claim.id()).orElse(null);
        var now = repository.now();
        return item != null && active.getAsBoolean() && item.renew(claim.token(), now, properties.getLeaseDuration());
    }

    @Transactional
    public boolean complete(Claim claim, Receipt result, BooleanSupplier active) {
        var item = repository.lock(claim.id()).orElse(null);
        var now = repository.now();
        if (item == null || !active.getAsBoolean() || !item.hasClaim(claim.token(), now)) return false;
        return item.complete(claim.token(), now, mapper.writeValueAsString(result));
    }

    @Transactional
    public boolean fail(Claim claim, boolean retryable) {
        var item = repository.lock(claim.id()).orElse(null);
        return item != null && item.fail(claim.token(), repository.now(), retryable);
    }

    @Transactional
    public void delete(Long id) { requireLocked(id).delete(); }

    private OcrItem requireLocked(Long id) { return repository.lock(id).orElseThrow(OcrExceptions.NotFound::new); }
}
