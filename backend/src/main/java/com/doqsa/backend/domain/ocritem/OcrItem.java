package com.doqsa.backend.domain.ocritem;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ocr_item", indexes = {
        @Index(name = "idx_ocr_queue", columnList = "deleted,status,next_attempt_at,lease_expires_at,id")
})
public class OcrItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255) private String fileName;
    @Column(nullable = false, length = 100) private String contentType;
    @Column(nullable = false, unique = true, length = 255) private String storageKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private OcrStatus status;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private int maxAttempts;
    @Column(name = "next_attempt_at") private LocalDateTime nextAttemptAt;
    @Column(name = "lease_expires_at") private LocalDateTime leaseExpiresAt;
    @Column(length = 128) private String processingOwner;
    @Column(length = 36) private String claimToken;
    @Lob private String resultJson;
    @Column(length = 50) private String failureCode;
    @Column(nullable = false) private boolean deleted;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static OcrItem create(String fileName, String contentType, String storageKey,
                                 int maxAttempts, LocalDateTime now) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        var item = new OcrItem();
        item.fileName = Objects.requireNonNull(fileName);
        item.contentType = Objects.requireNonNull(contentType);
        item.storageKey = Objects.requireNonNull(storageKey);
        item.maxAttempts = maxAttempts;
        item.createdAt = Objects.requireNonNull(now);
        item.status = OcrStatus.WAITING_UPLOAD;
        return item;
    }

    public boolean queueUploaded() {
        if (deleted || status != OcrStatus.WAITING_UPLOAD) return false;
        status = OcrStatus.UPLOADED;
        status = OcrStatus.QUEUED;
        return true;
    }

    public boolean claim(String owner, String token, LocalDateTime now, Duration lease) {
        if (deleted || status.terminal()) return false;
        boolean expired = status == OcrStatus.PROCESSING && leaseExpiresAt != null && !leaseExpiresAt.isAfter(now);
        boolean ready = status == OcrStatus.QUEUED && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
        if (!expired && !ready) return false;
        if (owner == null || owner.isBlank() || owner.length() > 128 || token == null || token.isBlank()
                || token.length() > 36 || lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("Valid owner, token and lease are required");
        }
        if (attempts >= maxAttempts) {
            failPermanently("RETRY_EXHAUSTED");
            return false;
        }
        attempts++;
        status = OcrStatus.PROCESSING;
        processingOwner = owner;
        claimToken = token;
        leaseExpiresAt = now.plus(lease);
        nextAttemptAt = null;
        failureCode = null;
        return true;
    }

    public boolean hasClaim(String token, LocalDateTime now) {
        return !deleted && status == OcrStatus.PROCESSING && token != null
                && Objects.equals(token, claimToken) && leaseExpiresAt != null && leaseExpiresAt.isAfter(now);
    }

    public boolean renew(String token, LocalDateTime now, Duration duration) {
        if (!hasClaim(token, now)) return false;
        leaseExpiresAt = now.plus(duration);
        return true;
    }

    public boolean complete(String token, LocalDateTime now, String json) {
        if (!hasClaim(token, now)) return false;
        resultJson = Objects.requireNonNull(json);
        completedAt = now;
        status = OcrStatus.COMPLETED;
        clearClaim();
        return true;
    }

    public boolean fail(String token, LocalDateTime now, boolean retryable) {
        if (!hasClaim(token, now)) return false;
        if (retryable && attempts < maxAttempts) {
            status = OcrStatus.QUEUED;
            nextAttemptAt = now.plusSeconds(1L << Math.min(attempts - 1, 10));
            failureCode = "RETRY_SCHEDULED";
            clearClaim();
        } else failPermanently(retryable ? "RETRY_EXHAUSTED" : "OCR_FAILED");
        return true;
    }

    public void delete() { deleted = true; clearClaim(); }

    private void failPermanently(String code) {
        status = OcrStatus.FAILED;
        failureCode = code;
        nextAttemptAt = null;
        clearClaim();
    }

    private void clearClaim() { processingOwner = null; claimToken = null; leaseExpiresAt = null; }
}
