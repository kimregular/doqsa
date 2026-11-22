package com.doqsa.backend.application.model;

import com.doqsa.backend.domain.ocritem.OcrItem;
import com.doqsa.backend.domain.ocritem.OcrStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class OcrModels {
    private OcrModels() { }
    public record Upload(String storageKey, String url, Instant expiresAt) { }
    public record Created(Long id, OcrStatus status, String uploadUrl, Instant expiresAt) { }
    public record Receipt(String name, String address, String tell, String mobile, String memo) { }
    public record View(Long id, String fileName, OcrStatus status, int attempts, String failureCode,
                       LocalDateTime createdAt, LocalDateTime completedAt, String imageUrl, Receipt result) { }
    public record Page(List<View> content, Long nextCursor, boolean hasNext) { }
    public record Claim(Long id, String token, String storageKey, String contentType) {
        public static Claim from(OcrItem item) {
            return new Claim(item.getId(), item.getClaimToken(), item.getStorageKey(), item.getContentType());
        }
    }
}
