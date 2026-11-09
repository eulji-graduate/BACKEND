package com.module06.backend.project.application.service;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.JwtProperties;
import com.module06.backend.project.application.port.LocalAttachmentFilePort;
import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;
import com.module06.backend.project.application.usecase.LocalAttachmentStorageUseCase;
import com.module06.backend.project.exception.ProjectErrorCode;

/** Separate HMAC scope from access JWTs and recording URLs; upload size is signed too. */
@Service
@Profile("local")
public class LocalProjectAttachmentStorageService implements ProjectAttachmentStoragePort, LocalAttachmentStorageUseCase {
    private final LocalAttachmentFilePort files;
    private final Clock clock;
    private final byte[] secret;
    private final String baseUrl;
    private final long maxBytes;

    public LocalProjectAttachmentStorageService(LocalAttachmentFilePort files, Clock clock, JwtProperties jwt,
            @Value("${app.local-storage.base-url:http://localhost:${server.port:8080}}") String baseUrl,
            @Value("${app.local-attachments.max-upload-bytes:104857600}") long maxBytes) {
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
        if (maxBytes <= 0 || maxBytes > 5368709120L) {
            throw new IllegalArgumentException("Local attachment limit must be between 1 byte and 5 GiB");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.maxBytes = maxBytes;
    }

    @Override
    public IssuedUploadUrl issueUploadUrl(String key, long fileSize) {
        validateSize(fileSize);
        return new IssuedUploadUrl(issue("PUT", key, fileSize, "", 900), key);
    }

    @Override
    public IssuedDownloadUrl issueDownloadUrl(String key) {
        validateKey(key);
        return issueDownloadUrl(key, key.substring(key.lastIndexOf('/') + 1));
    }

    @Override
    public IssuedDownloadUrl issueDownloadUrl(String key, String fileName) {
        validateKey(key);
        files.find(key);
        if (fileName == null || fileName.isBlank() || fileName.length() > 255
                || fileName.chars().anyMatch(Character::isISOControl)
                || fileName.contains("/") || fileName.contains("\\")) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        }
        return new IssuedDownloadUrl(issue("GET", key, 0, fileName, 300), 300);
    }

    @Override
    public void verifyUploaded(String key, long size) {
        validateKey(key);
        validateSize(size);
        if (!files.matches(key, size)) throw new BusinessException(ProjectErrorCode.ATTACHMENT_UPLOAD_INCOMPLETE);
    }

    @Override
    public void deleteObject(String key) { validateKey(key); files.delete(key); }

    @Override
    public boolean isAuthorized(String method, String token, String key) {
        if (!"PUT".equals(method) && !"GET".equals(method)) return false;
        try { return verify(method, token).key().equals(key); }
        catch (BusinessException | IllegalArgumentException error) { return false; }
    }

    @Override
    public void upload(String token, String key, InputStream input, long contentLength) {
        Claims claims = verify("PUT", token);
        if (!claims.key().equals(key)) throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIGNATURE_INVALID);
        validateSize(claims.size());
        if (contentLength >= 0 && contentLength != claims.size()) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_LENGTH_MISMATCH);
        }
        files.store(key, input, claims.size());
    }

    @Override
    public StoredAttachment download(String token, String key) {
        Claims claims = verify("GET", token);
        if (!claims.key().equals(key)) throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIGNATURE_INVALID);
        return new StoredAttachment(files.find(key), claims.fileName());
    }

    private String issue(String method, String key, long size, String fileName, int ttl) {
        validateKey(key);
        String payload = encode(key) + "." + size + "." + encode(fileName) + "." + (clock.instant().getEpochSecond() + ttl);
        String token = payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(method, payload));
        return UriComponentsBuilder.fromUriString(baseUrl + "/" + key).queryParam("token", token)
                .build().encode().toUriString();
    }

    private Claims verify(String method, String token) {
        try {
            if (token == null || token.length() > 4096) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 5) throw new IllegalArgumentException();
            String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(sign(method, payload), Base64.getUrlDecoder().decode(parts[4]))
                    || Long.parseLong(parts[3]) <= clock.instant().getEpochSecond()) throw new IllegalArgumentException();
            String key = decode(parts[0]);
            validateKey(key);
            return new Claims(key, Long.parseLong(parts[1]), decode(parts[2]));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIGNATURE_INVALID);
        }
    }

    private byte[] sign(String method, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(("local-project-attachment-v1\n" + method + "\n" + payload).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException error) { throw new IllegalStateException("Cannot sign attachment URL", error); }
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("project-attachments/company-[1-9][0-9]*/project-[1-9][0-9]*/[0-9a-f-]{36}(?:\\.[\\p{L}\\p{N}_-]{1,32})?")) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        }
    }

    private void validateSize(long size) {
        if (size <= 0 || size > maxBytes) throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIZE_INVALID);
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private record Claims(String key, long size, String fileName) { }
}
