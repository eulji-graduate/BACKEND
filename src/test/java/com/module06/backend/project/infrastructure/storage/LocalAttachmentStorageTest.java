package com.module06.backend.project.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.JwtProperties;
import com.module06.backend.project.application.service.LocalProjectAttachmentStorageService;
import com.module06.backend.project.exception.ProjectErrorCode;

class LocalAttachmentStorageTest {
    private static final String KEY = "project-attachments/company-1/project-2/12345678-1234-1234-1234-123456789abc.pdf";
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    @TempDir Path root;

    private LocalProjectAttachmentStorageService service(Instant now) {
        return new LocalProjectAttachmentStorageService(new LocalAttachmentFileAdapter(root.toString(), 32),
                Clock.fixed(now, ZoneOffset.UTC), new JwtProperties("ab".repeat(32), null, null, null, null),
                "http://localhost:8080", 32);
    }
    private String token(String url) { return URI.create(url).getRawQuery().substring("token=".length()); }

    @Test
    void expiresAtBoundaryAndBindsMethodKeyAndDeclaredSize() {
        var storage = service(NOW);
        String token = token(storage.issueUploadUrl(KEY, 4).uploadUrl());
        assertThat(storage.isAuthorized("PUT", token, KEY)).isTrue();
        assertThat(storage.isAuthorized("GET", token, KEY)).isFalse();
        assertThat(storage.isAuthorized("PUT", token, KEY.replace("company-1", "company-3"))).isFalse();
        assertThat(service(NOW.plusSeconds(900)).isAuthorized("PUT", token, KEY)).isFalse();
        assertThatThrownBy(() -> storage.upload(token, KEY, new ByteArrayInputStream(new byte[3]), 3))
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_LENGTH_MISMATCH);
        String[] parts = token.split("\\.", -1);
        parts[1] = "5";
        assertThat(storage.isAuthorized("PUT", String.join(".", parts), KEY)).isFalse();
    }

    @Test
    void chunkedUploadMustMatchSignedSizeAndLeavesNoPartialFile() {
        var storage = service(NOW);
        String token = token(storage.issueUploadUrl(KEY, 4).uploadUrl());
        assertThatThrownBy(() -> storage.upload(token, KEY, new ByteArrayInputStream(new byte[3]), -1))
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_LENGTH_MISMATCH);
        assertThat(root.resolve(KEY)).doesNotExist();
        assertThatThrownBy(() -> storage.upload(token, KEY, new ByteArrayInputStream(new byte[5]), -1))
                .isInstanceOf(BusinessException.class);
        assertThat(root.resolve(KEY)).doesNotExist();
        storage.upload(token, KEY, new ByteArrayInputStream(new byte[4]), -1);
        storage.verifyUploaded(KEY, 4);
        assertThatThrownBy(() -> storage.verifyUploaded(KEY, 3))
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_UPLOAD_INCOMPLETE);
    }

    @Test
    void refusesOverwriteAndDownloadExpiresAfterFiveMinutes() {
        var storage = service(NOW);
        String upload = token(storage.issueUploadUrl(KEY, 4).uploadUrl());
        storage.upload(upload, KEY, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 4);
        assertThatThrownBy(() -> storage.upload(upload, KEY, new ByteArrayInputStream(new byte[4]), 4))
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_OBJECT_EXISTS);
        String download = token(storage.issueDownloadUrl(KEY, "기획서.pdf").downloadUrl());
        assertThat(storage.download(download, KEY).fileName()).isEqualTo("기획서.pdf");
        assertThat(storage.isAuthorized("PUT", download, KEY)).isFalse();
        assertThat(service(NOW.plusSeconds(300)).isAuthorized("GET", download, KEY)).isFalse();
        assertThatThrownBy(() -> storage.issueDownloadUrl(KEY, "bad\r\nname.pdf")).isInstanceOf(BusinessException.class);
        storage.deleteObject(KEY);
        storage.deleteObject(KEY);
        assertThatThrownBy(() -> storage.download(download, KEY))
                .hasFieldOrPropertyWithValue("errorCode", ProjectErrorCode.ATTACHMENT_NOT_FOUND);
    }

    @Test
    void rejectsTraversalRecordingNamespaceAbsoluteAndInvalidKeys() {
        var storage = service(NOW);
        for (String key : new String[]{"../outside", "/tmp/test", "recordings/org-1/meeting-2/test.pdf",
                KEY.replace("project-2/", "project-2/../"), KEY.replace(".pdf", ".pdf/child")}) {
            assertThatThrownBy(() -> storage.issueUploadUrl(key, 4)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> storage.deleteObject(key)).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> storage.issueUploadUrl(KEY, 0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.issueUploadUrl(KEY, 33)).isInstanceOf(BusinessException.class);
    }

    @Test
    void symbolicLinkCannotReadWriteOrDeleteOutsideStorageRoot() throws Exception {
        Path outside = Files.createTempDirectory(root.getParent(), "attachment-outside-");
        try {
            Path directory = root.resolve("project-attachments/company-1");
            Files.createDirectories(directory);
            Files.createSymbolicLink(directory.resolve("project-2"), outside);
            Path sentinel = outside.resolve(KEY.substring(KEY.lastIndexOf('/') + 1));
            Files.write(sentinel, new byte[]{9});
            var storage = service(NOW);
            String token = token(storage.issueUploadUrl(KEY, 4).uploadUrl());
            assertThatThrownBy(() -> storage.upload(token, KEY, new ByteArrayInputStream(new byte[4]), 4))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> storage.issueDownloadUrl(KEY)).isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> storage.deleteObject(KEY)).isInstanceOf(BusinessException.class);
            assertThat(Files.readAllBytes(sentinel)).containsExactly((byte) 9);
        } finally { org.springframework.util.FileSystemUtils.deleteRecursively(outside); }
    }
}
