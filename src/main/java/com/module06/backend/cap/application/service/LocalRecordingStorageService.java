package com.module06.backend.cap.application.service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import com.module06.backend.cap.application.port.out.LocalRecordingFilePort;
import com.module06.backend.cap.application.usecase.LocalRecordingStorageUseCase;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.JwtProperties;

/** Local signed URLs are a separate credential type; their HMAC cannot be used as an access JWT. */
@Service
@Profile("local")
public class LocalRecordingStorageService implements LocalRecordingStorageUseCase {
    private static final String SIGNATURE_SCOPE = "local-recording-object";
    private final LocalRecordingFilePort files;
    private final Clock clock;
    private final byte[] secret;
    private final String baseUrl;

    public LocalRecordingStorageService(LocalRecordingFilePort files, Clock clock, JwtProperties jwt,
            @Value("${app.local-storage.base-url:http://localhost:${server.port:8080}}") String baseUrl) {
        this.files = files;
        this.clock = clock;
        this.secret = HexFormat.of().parseHex(jwt.secret().trim());
        URI uri = URI.create(baseUrl);
        if (!"http".equals(uri.getScheme()) || uri.getHost() == null
                || !java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("Local storage base URL must be a loopback HTTP origin");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String issueUploadUrl(String key, String contentType) {
        return issue("PUT", key, normalizeType(contentType), 900);
    }

    public String issuePlaybackUrl(String key) {
        files.find(key);
        return issue("GET", key, playbackType(key), 10800);
    }

    public boolean matches(String key, long size) { return files.matches(key, size); }
    public void delete(String key) { files.delete(key); }

    @Override
    public void upload(String token, String requestedKey, String contentType, InputStream input, long contentLength) {
        Claims claims = verify("PUT", token);
        if (!claims.key().equals(requestedKey) || !claims.contentType().equals(normalizeType(contentType))) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_SIGNATURE_INVALID);
        }
        files.store(claims.key(), input, contentLength);
    }

    @Override
    public StoredRecording playback(String token, String requestedKey) {
        Claims claims = verify("GET", token);
        if (!claims.key().equals(requestedKey)) throw new BusinessException(CapErrorCode.CAP_LOCAL_SIGNATURE_INVALID);
        return new StoredRecording(files.find(claims.key()), claims.contentType());
    }

    private String issue(String method, String key, String contentType, int seconds) {
        validateKey(key);
        String payload = encode(key) + "." + encode(contentType) + "." + (clock.instant().getEpochSecond() + seconds);
        String token = payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(method, payload));
        return org.springframework.web.util.UriComponentsBuilder.fromUriString(baseUrl + "/" + key)
                .queryParam("token", token).build().encode().toUriString();
    }

    private Claims verify(String method, String token) {
        try {
            if (token == null || token.length() > 4096) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 4) throw new IllegalArgumentException();
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!MessageDigest.isEqual(sign(method, payload), Base64.getUrlDecoder().decode(parts[3]))
                    || Long.parseLong(parts[2]) <= clock.instant().getEpochSecond()) {
                throw new IllegalArgumentException();
            }
            String key = decode(parts[0]);
            validateKey(key);
            return new Claims(key, decode(parts[1]));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_SIGNATURE_INVALID);
        }
    }

    private byte[] sign(String method, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(("local-recording-v1\n" + method + "\n" + SIGNATURE_SCOPE + "\n" + payload)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Cannot sign local storage URL", error);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.length() > 1024
                || !key.matches("(?:recordings/org-[1-9][0-9]*/(?:meeting-[1-9][0-9]*|member-[1-9][0-9]*/online-pending)|stt-temp/org-[1-9][0-9]*/meeting-[1-9][0-9]*)/[\\p{L}\\p{N} _./()+-]+")
                || key.contains("..") || key.contains("//") || key.endsWith("/")) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
        }
    }

    private String normalizeType(String value) {
        try {
            MediaType type = MediaType.parseMediaType(value);
            if (type.isWildcardType() || type.isWildcardSubtype()
                    || !(type.getType().equals("audio") || type.isCompatibleWith(MediaType.parseMediaType("video/mp4"))
                    || type.isCompatibleWith(MediaType.parseMediaType("video/webm"))
                    || type.equals(MediaType.parseMediaType("application/mp4"))
                    || type.equals(MediaType.parseMediaType("application/ogg"))
                    || type.equals(MediaType.APPLICATION_OCTET_STREAM))) throw new IllegalArgumentException();
            return type.toString();
        } catch (IllegalArgumentException error) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_FORMAT_UNSUPPORTED);
        }
    }

    private String playbackType(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) return "audio/mp4";
        if (lower.endsWith(".webm")) return "audio/webm";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".amr")) return "audio/amr";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "application/octet-stream";
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private record Claims(String key, String contentType) { }
}
