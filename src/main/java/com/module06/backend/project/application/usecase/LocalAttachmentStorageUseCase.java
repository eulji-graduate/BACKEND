package com.module06.backend.project.application.usecase;

import java.io.InputStream;
import java.nio.file.Path;

public interface LocalAttachmentStorageUseCase {
    boolean isAuthorized(String method, String token, String key);
    void upload(String token, String key, InputStream input, long contentLength);
    StoredAttachment download(String token, String key);
    record StoredAttachment(Path path, String fileName) { }
}
