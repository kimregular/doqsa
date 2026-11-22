package com.doqsa.backend;

import com.doqsa.backend.adapter.out.persistence.OcrItemJpaRepository;
import com.doqsa.backend.application.model.OcrModels.*;
import com.doqsa.backend.application.port.in.OcrUseCase;
import com.doqsa.backend.application.port.out.*;
import com.doqsa.backend.application.service.*;
import com.doqsa.backend.domain.ocritem.OcrStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ExtendWith(RestDocumentationExtension.class)
class OcrIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired OcrUseCase ocr;
    @Autowired OcrStateService states;
    @Autowired OcrProcessor processor;
    @Autowired OcrItemRepository repository;
    @Autowired OcrItemJpaRepository jpa;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper mapper;
    @Autowired TransactionTemplate transactions;
    @MockitoBean OcrImageAnalyzer analyzer;
    MockMvc mvc;
    private static final Receipt RESULT = new Receipt("홍길동", "서울시", "02-123-4567", null, "문 앞");

    @BeforeEach void setup(RestDocumentationContextProvider docs) {
        jpa.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(documentationConfiguration(docs)).build();
        when(analyzer.analyze(any(), anyString())).thenReturn(RESULT);
    }

    private Created create() { return ocr.create("receipt.png", "image/png"); }
    private Long uploadAndQueue() throws Exception {
        var created = create();
        mvc.perform(put(created.uploadUrl()).contentType("image/png").content(new byte[]{1, 2, 3})).andExpect(status().isNoContent());
        ocr.uploaded(created.id());
        return created.id();
    }
    private void due(Long id) {
        jdbc.update("update ocr_item set next_attempt_at = ?, lease_expires_at = ? where id = ?",
                repository.now().minusSeconds(1), repository.now().minusSeconds(1), id);
    }

    @Test void uploadToResultAndRestDocs() throws Exception {
        var response = mvc.perform(post("/api/ocr/items").contentType("application/json")
                        .content("{\"fileName\":\"receipt.png\",\"contentType\":\"image/png\"}"))
                .andExpect(status().isCreated()).andDo(document("create-item")).andReturn();
        var created = mapper.readValue(response.getResponse().getContentAsString(), Created.class);
        mvc.perform(put(created.uploadUrl()).contentType("image/png").content(new byte[]{1, 2, 3})).andExpect(status().isNoContent());
        mvc.perform(post("/api/ocr/items/{id}/upload-complete", created.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED")).andDo(document("upload-complete"));
        processor.process(created.id());
        mvc.perform(get("/api/ocr/items/{id}", created.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.result.name").value("홍길동"))
                .andDo(document("get-item"));
        mvc.perform(get("/api/ocr/items")).andExpect(status().isOk()).andDo(document("list-items"));
        var pending = create();
        mvc.perform(post("/api/ocr/items/{id}/upload-url", pending.id())).andExpect(status().isOk()).andDo(document("reissue-upload"));
        mvc.perform(delete("/api/ocr/items/{id}", created.id())).andExpect(status().isNoContent()).andDo(document("delete-item"));
        mvc.perform(get("/api/ocr/items/{id}", created.id())).andExpect(status().isNotFound());
    }

    @Test void uploadAckIsIdempotentAndMissingObjectCanBeRetried() throws Exception {
        var created = create();
        mvc.perform(post("/api/ocr/items/{id}/upload-complete", created.id())).andExpect(status().isConflict());
        assertThat(ocr.get(created.id()).status()).isEqualTo(OcrStatus.WAITING_UPLOAD);
        mvc.perform(put(created.uploadUrl()).content(new byte[]{1})).andExpect(status().isNoContent());
        ocr.uploaded(created.id());
        ocr.uploaded(created.id());
        processor.process(created.id());
        ocr.uploaded(created.id());
        processor.process(created.id());
        verify(analyzer, times(1)).analyze(any(), anyString());
    }

    @Test void cursorPaginationExcludesDeletedItems() {
        var first = create();
        var second = create();
        var third = create();
        ocr.delete(second.id());
        var page = ocr.list(null, 1);
        assertThat(page.content()).extracting(View::id).containsExactly(third.id());
        assertThat(ocr.list(page.nextCursor(), 1).content()).extracting(View::id).containsExactly(first.id());
    }

    @Test void invalidInputReturns400() throws Exception {
        mvc.perform(post("/api/ocr/items").contentType("application/json")
                .content("{\"fileName\":\"x.pdf\",\"contentType\":\"application/pdf\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ocr/items?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ocr/items/-1")).andExpect(status().isBadRequest());
    }

    @Test void onlyOneConcurrentWorkerClaimsAnItem() throws Exception {
        var id = uploadAndQueue();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> attempt = () -> { barrier.await(); return states.claim(id).isPresent(); };
            var results = pool.invokeAll(List.of(attempt, attempt));
            assertThat(results.stream().filter(result -> {
                try { return result.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).count()).isEqualTo(1);
        }
    }

    @Test void expiredClaimCanBeRecoveredAndOldTokenCannotMutateResult() throws Exception {
        var id = uploadAndQueue();
        var old = states.claim(id).orElseThrow();
        due(id);
        assertThat(repository.runnable(10)).contains(id);
        var current = states.claim(id).orElseThrow();
        assertThat(states.complete(old, RESULT, () -> true)).isFalse();
        assertThat(states.renew(old, () -> true)).isFalse();
        assertThat(states.fail(old, true)).isFalse();
        assertThat(states.complete(current, RESULT, () -> true)).isTrue();
        assertThat(ocr.get(id).attempts()).isEqualTo(2);
    }

    @Test void retryBackoffAndExhaustionArePersisted() throws Exception {
        var id = uploadAndQueue();
        when(analyzer.analyze(any(), anyString())).thenThrow(new OcrExceptions.Retryable("provider unavailable"));
        for (int i = 0; i < 4; i++) {
            processor.process(id);
            if (i < 3) {
                assertThat(ocr.get(id).status()).isEqualTo(OcrStatus.QUEUED);
                assertThat(repository.runnable(10)).doesNotContain(id);
                due(id);
            }
        }
        assertThat(ocr.get(id).status()).isEqualTo(OcrStatus.FAILED);
        assertThat(ocr.get(id).failureCode()).isEqualTo("RETRY_EXHAUSTED");
        processor.process(id);
        verify(analyzer, times(4)).analyze(any(), anyString());
    }

    @Test void rollbackDoesNotQueueAndDeletedClaimsCannotComplete() throws Exception {
        var created = create();
        transactions.executeWithoutResult(tx -> { states.uploaded(created.id()); tx.setRollbackOnly(); });
        assertThat(ocr.get(created.id()).status()).isEqualTo(OcrStatus.WAITING_UPLOAD);
        var id = uploadAndQueue();
        var claim = states.claim(id).orElseThrow();
        ocr.delete(id);
        assertThat(states.complete(claim, RESULT, () -> true)).isFalse();
        assertThat(repository.runnable(10)).doesNotContain(id);
    }

    @Test void inactiveTaskCannotRenewOrStore() throws Exception {
        var id = uploadAndQueue();
        var claim = states.claim(id).orElseThrow();
        assertThat(states.renew(claim, () -> false)).isFalse();
        assertThat(states.complete(claim, RESULT, () -> false)).isFalse();
        assertThat(ocr.get(id).result()).isNull();
    }
}
