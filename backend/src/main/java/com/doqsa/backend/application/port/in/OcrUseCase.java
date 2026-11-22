package com.doqsa.backend.application.port.in;

import com.doqsa.backend.application.model.OcrModels.*;

public interface OcrUseCase {
    Created create(String fileName, String contentType);
    View uploaded(Long id);
    View get(Long id);
    Page list(Long cursor, int size);
    Upload reissueUpload(Long id);
    void delete(Long id);
}
