package com.module06.backend.cap.infrastructure.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.cap.application.service.LocalRecordingStorageService;

@Component
@Profile("local")
public class LocalCapObjectStorageAdapter implements CapObjectStoragePort {
    private final LocalRecordingStorageService storage;
    public LocalCapObjectStorageAdapter(LocalRecordingStorageService storage) { this.storage = storage; }
    @Override
    public IssuedPartUploadUrl issuePartUploadUrl(String key, String contentType) {
        return new IssuedPartUploadUrl(storage.issueUploadUrl(key, contentType), 900);
    }
    @Override
    public IssuedPlaybackUrl issuePlaybackUrl(String key) {
        return new IssuedPlaybackUrl(storage.issuePlaybackUrl(key), 10800);
    }
    @Override
    public void deleteRecording(String key) { storage.delete(key); }
    @Override
    public boolean objectMatches(String key, long size) { return storage.matches(key, size); }
}
