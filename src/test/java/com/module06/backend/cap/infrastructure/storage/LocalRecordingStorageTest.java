package com.module06.backend.cap.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.module06.backend.cap.application.service.LocalRecordingStorageService;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.JwtProperties;

class LocalRecordingStorageTest {
    @TempDir Path root;
    private static final String KEY = "recordings/org-1/meeting-2/test.wav";
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final JwtProperties JWT = new JwtProperties("0123456789abcdef".repeat(4),
            Duration.ofMinutes(30),Duration.ofDays(1),Duration.ofDays(14),Duration.ofDays(30));

    private LocalRecordingStorageService service(long seconds) {
        return new LocalRecordingStorageService(new LocalRecordingFileAdapter(root.toString(),32),
                Clock.fixed(NOW.plusSeconds(seconds), ZoneOffset.UTC), JWT, "http://localhost:8080");
    }
    private String token(String url) { return url.substring(url.indexOf("?token=")+7); }
    private ByteArrayInputStream data() { return new ByteArrayInputStream(new byte[]{1,2,3,4}); }

    @Test
    void persistsAcrossAdapterRestartAndDeletesRealFile() {
        var storage = service(0);
        String upload = token(storage.issueUploadUrl(KEY,"audio/wav"));
        storage.upload(upload,KEY,"audio/wav",data(),4);
        assertThat(service(1).matches(KEY,4)).isTrue();
        assertThat(storage.matches(KEY,5)).isFalse();
        String playback = token(storage.issuePlaybackUrl(KEY));
        assertThat(service(2).playback(playback,KEY).path()).exists();
        service(2).delete(KEY);
        assertThat(storage.matches(KEY,4)).isFalse();
        assertThatThrownBy(() -> storage.playback(playback,KEY)).isInstanceOf(BusinessException.class);
        storage.delete(KEY);
    }

    @Test
    void bindsMethodKeyExpiryAndContentTypeToSignature() {
        var storage = service(0);
        String upload = token(storage.issueUploadUrl(KEY,"audio/wav"));
        assertThatThrownBy(() -> service(900).upload(upload,KEY,"audio/wav",data(),4)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.playback(upload,KEY)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.upload(upload,KEY,"audio/mp3",data(),4)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> storage.upload("e"+upload,KEY,"audio/wav",data(),4)).isInstanceOf(BusinessException.class);
        storage.upload(upload,KEY,"audio/wav",data(),4);
        String playback = token(storage.issuePlaybackUrl(KEY));
        assertThatThrownBy(() -> storage.upload(playback,KEY,"audio/wav",data(),4)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service(10800).playback(playback,KEY)).isInstanceOf(BusinessException.class);
    }

    @Test
    void methodSecurityRequiresValidSignatureAndExactFileScopeWithoutFileAccess() {
        var storage = service(0);
        String upload = token(storage.issueUploadUrl(KEY,"audio/wav"));
        assertThat(storage.isAuthorized("PUT",upload,KEY,"audio/wav")).isTrue();
        assertThat(service(900).isAuthorized("PUT",upload,KEY,"audio/wav")).isFalse();
        assertThat(storage.isAuthorized("GET",upload,KEY,null)).isFalse();
        assertThat(storage.isAuthorized("PUT",upload,"recordings/org-2/meeting-2/test.wav","audio/wav")).isFalse();
        assertThat(storage.isAuthorized("PUT",upload,KEY,"audio/mp3")).isFalse();
        assertThat(storage.isAuthorized("PUT",upload,KEY,"text/html")).isFalse();
        assertThat(storage.isAuthorized("PUT",null,KEY,"audio/wav")).isFalse();
        assertThat(root.resolve(KEY)).doesNotExist();
        storage.upload(upload,KEY,"audio/wav",data(),4);
        String playback = token(storage.issuePlaybackUrl(KEY));
        assertThat(storage.isAuthorized("GET",playback,KEY,null)).isTrue();
        assertThat(storage.isAuthorized("DELETE",playback,KEY,null)).isFalse();
    }

    @Test
    void refusesOverwriteAndCleansPartialOrOversizedUploads() throws Exception {
        var files = new LocalRecordingFileAdapter(root.toString(),32);
        files.store(KEY,data(),4);
        assertThatThrownBy(() -> files.store(KEY,data(),4)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CapErrorCode.CAP_LOCAL_OBJECT_EXISTS));
        for (long length : List.of(0L,3L,33L)) {
            assertThatThrownBy(() -> files.store("different.wav",data(),length)).isInstanceOf(BusinessException.class);
            assertThat(root.resolve("different.wav")).doesNotExist();
        }
        assertThatThrownBy(() -> files.store("oversized.wav",new ByteArrayInputStream(new byte[33]),-1))
                .isInstanceOf(BusinessException.class);
        InputStream broken = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("interrupted"); }
        };
        assertThatThrownBy(() -> files.store("broken.wav",broken,-1)).isInstanceOf(BusinessException.class);
        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(p -> p.getFileName().toString().startsWith(".upload-")).count()).isZero();
        }
        assertThat(Files.readAllBytes(files.find(KEY))).containsExactly(1,2,3,4);
    }

    @Test
    void concurrentUploadsPublishOneCompleteFileWithoutOverwrite() throws Exception {
        var files = new LocalRecordingFileAdapter(root.toString(),32);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> upload = () -> {
                start.await();
                try { files.store(KEY,data(),4); return true; }
                catch (BusinessException error) {
                    assertThat(error.getErrorCode()).isEqualTo(CapErrorCode.CAP_LOCAL_OBJECT_EXISTS);
                    return false;
                }
            };
            var first = executor.submit(upload);
            var second = executor.submit(upload);
            start.countDown();
            assertThat(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(Files.readAllBytes(files.find(KEY))).containsExactly(1,2,3,4);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void rejectsTraversalAndSymlinkPathsAndInvalidObjectKeys() throws Exception {
        var files = new LocalRecordingFileAdapter(root.toString(),32);
        for (String key : List.of("../outside", "/outside", "a/../outside", "a\\b")) {
            assertThatThrownBy(() -> files.store(key,data(),4)).isInstanceOf(BusinessException.class);
        }
        Path outside = Files.createDirectory(root.resolve("outside"));
        Files.createSymbolicLink(root.resolve("link"),outside);
        assertThatThrownBy(() -> files.store("link/audio.wav",data(),4)).isInstanceOf(BusinessException.class);
        assertThat(outside.resolve("audio.wav")).doesNotExist();
        for (String key : List.of("recordings/org-1/meeting-2/../../escape.wav", "https://bucket/audio.wav", "other/audio.wav")) {
            assertThatThrownBy(() -> service(0).issueUploadUrl(key,"audio/wav")).isInstanceOf(BusinessException.class);
        }
    }
}
