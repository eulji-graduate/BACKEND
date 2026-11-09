package com.module06.backend.cap.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.module06.backend.cap.application.port.out.LocalRecordingFilePort;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.RecordingFilePolicy;
import com.module06.backend.global.exception.BusinessException;

@Component
@Profile("local")
public class LocalRecordingFileAdapter implements LocalRecordingFilePort {
    private final Path root;
    private final long maxBytes;

    public LocalRecordingFileAdapter(@Value("${app.local-storage.root:.local/storage}") String root,
            @Value("${app.local-storage.max-upload-bytes:5368709120}") long maxBytes) {
        if (root == null || root.isBlank()) throw new IllegalArgumentException("Local storage root is required");
        Path configured = Path.of(root).toAbsolutePath().normalize();
        try {
            this.root = Files.exists(configured) ? configured.toRealPath() : configured;
        } catch (IOException error) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_STORAGE_FAILED, error);
        }
        if (maxBytes <= 0 || maxBytes > RecordingFilePolicy.MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Local upload size limit must be within the recording size limit");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public void store(String key, InputStream input, long contentLength) {
        if (contentLength == 0 || contentLength > maxBytes) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_SIZE_INVALID);
        }
        Path target = resolve(key);
        Path temporary = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(CapErrorCode.CAP_LOCAL_OBJECT_EXISTS);
            }
            Files.createDirectories(target.getParent());
            resolve(key);
            temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            long size = 0;
            try (OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > maxBytes) throw new BusinessException(CapErrorCode.CAP_RECORDING_SIZE_INVALID);
                    output.write(buffer, 0, read);
                }
            }
            if (size == 0) throw new BusinessException(CapErrorCode.CAP_RECORDING_SIZE_INVALID);
            if (contentLength >= 0 && size != contentLength) {
                throw new BusinessException(CapErrorCode.CAP_LOCAL_LENGTH_MISMATCH);
            }
            resolve(key);
            // Publish the completed inode exclusively: concurrent uploads cannot overwrite each other.
            Files.createLink(target, temporary);
        } catch (FileAlreadyExistsException error) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_OBJECT_EXISTS);
        } catch (IOException error) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_STORAGE_FAILED, error);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException error) {
                    org.slf4j.LoggerFactory.getLogger(getClass()).warn("Incomplete upload temporary file cleanup failed", error);
                }
            }
        }
    }

    @Override
    public Path find(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(CapErrorCode.CAP_RECORDING_NOT_FOUND);
        }
        return path;
    }

    @Override
    public boolean matches(String key, long size) {
        Path path = resolve(key);
        try {
            return size > 0 && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == size;
        } catch (IOException error) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_STORAGE_FAILED, error);
        }
    }

    @Override
    public void delete(String key) {
        Path path = resolve(key);
        try { Files.deleteIfExists(path); }
        catch (IOException error) { throw new BusinessException(CapErrorCode.CAP_LOCAL_STORAGE_FAILED, error); }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.indexOf(0) >= 0 || key.contains(":") || key.contains("\\")) {
            throw new BusinessException(CapErrorCode.CAP_LOCAL_PATH_INVALID);
        }
        Path relative = Path.of(key);
        if (relative.isAbsolute()) throw new BusinessException(CapErrorCode.CAP_LOCAL_PATH_INVALID);
        for (Path part : relative) {
            if (part.toString().equals(".") || part.toString().equals("..")) {
                throw new BusinessException(CapErrorCode.CAP_LOCAL_PATH_INVALID);
            }
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new BusinessException(CapErrorCode.CAP_LOCAL_PATH_INVALID);
        Path cursor = target.getRoot();
        for (Path part : target) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new BusinessException(CapErrorCode.CAP_LOCAL_PATH_INVALID);
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new BusinessException(CapErrorCode.CAP_LOCAL_PATH_INVALID);
        return target;
    }
}
