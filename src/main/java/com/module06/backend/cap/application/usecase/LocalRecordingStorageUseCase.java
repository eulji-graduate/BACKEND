package com.module06.backend.cap.application.usecase;

import java.io.InputStream;
import java.nio.file.Path;

public interface LocalRecordingStorageUseCase {
    void upload(String token, String requestedKey, String contentType, InputStream input, long contentLength);
    StoredRecording playback(String token, String requestedKey);
    record StoredRecording(Path path, String contentType) { }
}
