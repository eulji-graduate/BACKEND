package com.module06.backend.cap.presentation.api;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import com.module06.backend.cap.application.usecase.LocalRecordingStorageUseCase;
import jakarta.servlet.http.HttpServletRequest;

/** Signed URLs authorize anonymous file requests; access-JWT roles do not grant file access here. */
@Component("localRecordingUrlAuthorization")
@Profile("local")
public class LocalRecordingUrlAuthorization {
    private final LocalRecordingStorageUseCase storage;
    public LocalRecordingUrlAuthorization(LocalRecordingStorageUseCase storage) { this.storage = storage; }

    public boolean authorize(String token, HttpServletRequest request) {
        try {
            String key = UriUtils.decode(request.getRequestURI().substring(1), StandardCharsets.UTF_8);
            String method = "HEAD".equals(request.getMethod()) ? "GET" : request.getMethod();
            return storage.isAuthorized(method, token, key, request.getContentType());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
