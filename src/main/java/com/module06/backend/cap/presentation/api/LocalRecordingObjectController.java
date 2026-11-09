package com.module06.backend.cap.presentation.api;

import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.module06.backend.cap.application.usecase.LocalRecordingStorageUseCase;
import jakarta.servlet.http.HttpServletRequest;

/** Direct file endpoint: the signed URL authorizes this request instead of a bearer token. */
@RestController
@Profile("local")
public class LocalRecordingObjectController {
    private final LocalRecordingStorageUseCase storage;
    public LocalRecordingObjectController(LocalRecordingStorageUseCase storage) { this.storage = storage; }

    @PutMapping({"/recordings/{*objectKey}", "/stt-temp/{*objectKey}"})
    public ResponseEntity<Void> upload(@RequestParam(required = false) String token, HttpServletRequest request)
            throws IOException {
        storage.upload(token, requestKey(request), request.getContentType(), request.getInputStream(), request.getContentLengthLong());
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping({"/recordings/{*objectKey}", "/stt-temp/{*objectKey}"})
    public ResponseEntity<Resource> playback(@RequestParam(required = false) String token, HttpServletRequest request) {
        var file = storage.playback(token, requestKey(request));
        // Spring MVC's Resource handling provides byte ranges, 206, 416, and HEAD responses.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file.path()));
    }
    private String requestKey(HttpServletRequest request) {
        return org.springframework.web.util.UriUtils.decode(request.getRequestURI().substring(1),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
