package com.doqsa.backend.domain;

import com.doqsa.backend.domain.ocritem.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class OcrItemTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
    private OcrItem queued(int maxAttempts) {
        var item = OcrItem.create("receipt.png", "image/png", "key", maxAttempts, now);
        item.queueUploaded();
        return item;
    }
    @Test void cannotClaimBeforeUpload() {
        var item = OcrItem.create("a.png", "image/png", "key", 4, now);
        assertThat(item.claim("worker", "token", now, Duration.ofSeconds(10))).isFalse();
    }
    @Test void liveClaimCannotBeReplaced() {
        var item = queued(4);
        assertThat(item.claim("worker1", "first", now, Duration.ofSeconds(10))).isTrue();
        assertThat(item.claim("worker2", "second", now.plusSeconds(9), Duration.ofSeconds(10))).isFalse();
        assertThat(item.getAttempts()).isEqualTo(1);
    }
    @Test void expiredWorkerCannotStoreOrRenew() {
        var item = queued(4);
        item.claim("worker1", "first", now, Duration.ofSeconds(10));
        assertThat(item.claim("worker2", "second", now.plusSeconds(10), Duration.ofSeconds(10))).isTrue();
        assertThat(item.complete("first", now.plusSeconds(11), "{}")).isFalse();
        assertThat(item.renew("first", now.plusSeconds(11), Duration.ofSeconds(10))).isFalse();
        assertThat(item.fail("first", now.plusSeconds(11), true)).isFalse();
        assertThat(item.complete("second", now.plusSeconds(11), "{}")).isTrue();
    }
    @Test void retryHasBackoffAndBoundedAttempts() {
        var item = queued(2);
        item.claim("worker", "first", now, Duration.ofSeconds(10));
        item.fail("first", now, true);
        assertThat(item.claim("worker", "second", now, Duration.ofSeconds(10))).isFalse();
        assertThat(item.claim("worker", "second", now.plusSeconds(1), Duration.ofSeconds(10))).isTrue();
        item.fail("second", now.plusSeconds(1), true);
        assertThat(item.getStatus()).isEqualTo(OcrStatus.FAILED);
        assertThat(item.getFailureCode()).isEqualTo("RETRY_EXHAUSTED");
    }
    @Test void crashRecoveryAlsoHonorsAttemptLimit() {
        var item = queued(1);
        item.claim("worker", "first", now, Duration.ofSeconds(1));
        assertThat(item.claim("worker", "second", now.plusSeconds(2), Duration.ofSeconds(1))).isFalse();
        assertThat(item.getStatus()).isEqualTo(OcrStatus.FAILED);
    }
    @Test void deletedAndCompletedItemsCannotRunAgain() {
        var item = queued(4);
        item.claim("worker", "first", now, Duration.ofSeconds(10));
        item.delete();
        assertThat(item.complete("first", now, "{}")).isFalse();
        assertThat(item.claim("worker", "second", now.plusSeconds(20), Duration.ofSeconds(10))).isFalse();
        var completed = queued(4);
        completed.claim("worker", "first", now, Duration.ofSeconds(10));
        completed.complete("first", now, "{}");
        assertThat(completed.claim("worker", "second", now, Duration.ofSeconds(10))).isFalse();
    }
}
