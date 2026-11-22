package com.doqsa.backend.application.port.out;

import com.doqsa.backend.application.model.OcrModels.Upload;

public interface ImageStorage {
    Upload issueUpload(String fileName, String contentType);
    Upload reissueUpload(String storageKey, String contentType);
    boolean exists(String storageKey);
    byte[] read(String storageKey);
    String readUrl(String storageKey);
}
