package com.doqsa.backend.adapter.out.persistence;

import com.doqsa.backend.domain.ocritem.*;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface OcrItemJpaRepository extends JpaRepository<OcrItem, Long> {
    Optional<OcrItem> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select i from OcrItem i where i.id = :id and i.deleted = false")
    Optional<OcrItem> lock(@Param("id") Long id);

    @Query("select i from OcrItem i where i.deleted = false and i.id < :cursor order by i.id desc")
    List<OcrItem> page(@Param("cursor") Long cursor, Pageable page);

    @Query("""
            select i.id from OcrItem i where i.deleted = false and
              ((i.status = :queued and (i.nextAttemptAt is null or i.nextAttemptAt <= CURRENT_TIMESTAMP)) or
               (i.status = :processing and i.leaseExpiresAt <= CURRENT_TIMESTAMP))
            order by i.id
            """)
    List<Long> runnable(@Param("queued") OcrStatus queued, @Param("processing") OcrStatus processing, Pageable page);

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    LocalDateTime databaseNow();
}
