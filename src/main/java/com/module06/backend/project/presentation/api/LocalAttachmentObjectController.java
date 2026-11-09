package com.module06.backend.project.presentation.api;

import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import com.module06.backend.project.exception.ProjectErrorCode;
import com.module06.backend.global.exception.ErrorResponse;
import com.module06.backend.global.audit.AuthzAuditLogger;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.module06.backend.project.application.usecase.LocalAttachmentStorageUseCase;
import jakarta.servlet.http.HttpServletRequest;

/** Direct file endpoint: the signed URL authorizes this request instead of a bearer token. */
@RestController
@Profile("local")
public class LocalAttachmentObjectController {
    private final LocalAttachmentStorageUseCase storage;
    public LocalAttachmentObjectController(LocalAttachmentStorageUseCase storage) { this.storage = storage; }

    @PreAuthorize("@localAttachmentUrlAuthorization.authorize(#token, #request)")
    @PutMapping("/project-attachments/{*objectKey}")
    public ResponseEntity<Void> upload(@RequestParam(required = false) String token, HttpServletRequest request)
            throws IOException {
        storage.upload(token, requestKey(request), request.getInputStream(), request.getContentLengthLong());
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PreAuthorize("@localAttachmentUrlAuthorization.authorize(#token, #request)")
    @GetMapping("/project-attachments/{*objectKey}")
    public ResponseEntity<Resource> download(@RequestParam(required = false) String token, HttpServletRequest request) {
        var file = storage.download(token, requestKey(request));
        // Spring MVC's Resource handling provides byte ranges, 206, 416, and HEAD responses.
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> invalidSignedUrl(HttpServletRequest request) {
        AuthzAuditLogger.deniedByFilter(request, ProjectErrorCode.ATTACHMENT_SIGNATURE_INVALID.getCode());
        return ResponseEntity.status(403).body(ErrorResponse.of(ProjectErrorCode.ATTACHMENT_SIGNATURE_INVALID,
                request.getRequestURI(), org.slf4j.MDC.get("traceId")));
    }

    private String requestKey(HttpServletRequest request) {
        return org.springframework.web.util.UriUtils.decode(request.getRequestURI().substring(1),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
