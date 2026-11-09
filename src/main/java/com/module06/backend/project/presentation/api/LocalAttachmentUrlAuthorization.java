package com.module06.backend.project.presentation.api;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import com.module06.backend.project.application.usecase.LocalAttachmentStorageUseCase;
import jakarta.servlet.http.HttpServletRequest;

/** Signed URLs authorize anonymous file requests; access-JWT roles do not grant file access here. */
@Component("localAttachmentUrlAuthorization")
@Profile("local")
public class LocalAttachmentUrlAuthorization {
    private final LocalAttachmentStorageUseCase storage;
    public LocalAttachmentUrlAuthorization(LocalAttachmentStorageUseCase storage) { this.storage = storage; }

    public boolean authorize(String token, HttpServletRequest request) {
        try {
            String key = UriUtils.decode(request.getRequestURI().substring(1), StandardCharsets.UTF_8);
            String method = "HEAD".equals(request.getMethod()) ? "GET" : request.getMethod();
            return storage.isAuthorized(method, token, key);
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
