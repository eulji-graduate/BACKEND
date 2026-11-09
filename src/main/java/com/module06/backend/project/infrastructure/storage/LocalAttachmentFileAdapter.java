package com.module06.backend.project.infrastructure.storage;

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
import com.module06.backend.project.application.port.LocalAttachmentFilePort;
import com.module06.backend.project.exception.ProjectErrorCode;
import com.module06.backend.global.exception.BusinessException;

@Component
@Profile("local")
public class LocalAttachmentFileAdapter implements LocalAttachmentFilePort {
    private final Path root;
    private final long maxBytes;

    public LocalAttachmentFileAdapter(@Value("${app.local-storage.root:.local/storage}") String root,
            @Value("${app.local-attachments.max-upload-bytes:104857600}") long maxBytes) {
        if (root == null || root.isBlank()) throw new IllegalArgumentException("Local storage root is required");
        Path configured = Path.of(root).toAbsolutePath().normalize();
        try {
            this.root = Files.exists(configured) ? configured.toRealPath() : configured;
        } catch (IOException error) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_STORAGE_FAILED, error);
        }
        if (maxBytes <= 0 || maxBytes > 5368709120L) {
            throw new IllegalArgumentException("Local upload size limit must be between 1 byte and 5 GiB");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public void store(String key, InputStream input, long contentLength) {
        if (contentLength <= 0 || contentLength > maxBytes) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIZE_INVALID);
        }
        Path target = resolve(key);
        Path temporary = null;
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new BusinessException(ProjectErrorCode.ATTACHMENT_OBJECT_EXISTS);
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
                    if (size > maxBytes || size > contentLength) throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIZE_INVALID);
                    output.write(buffer, 0, read);
                }
            }
            if (size == 0) throw new BusinessException(ProjectErrorCode.ATTACHMENT_SIZE_INVALID);
            if (contentLength >= 0 && size != contentLength) {
                throw new BusinessException(ProjectErrorCode.ATTACHMENT_LENGTH_MISMATCH);
            }
            resolve(key);
            // Publish the completed inode exclusively: concurrent uploads cannot overwrite each other.
            Files.createLink(target, temporary);
        } catch (FileAlreadyExistsException error) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_OBJECT_EXISTS);
        } catch (IOException error) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_STORAGE_FAILED, error);
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
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return path;
    }

    @Override
    public boolean matches(String key, long size) {
        Path path = resolve(key);
        try {
            return size > 0 && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == size;
        } catch (IOException error) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_STORAGE_FAILED, error);
        }
    }

    @Override
    public void delete(String key) {
        Path path = resolve(key);
        try { Files.deleteIfExists(path); }
        catch (IOException error) { throw new BusinessException(ProjectErrorCode.ATTACHMENT_STORAGE_FAILED, error); }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("project-attachments/company-[1-9][0-9]*/project-[1-9][0-9]*/[0-9a-f-]{36}(?:\\.[\\p{L}\\p{N}_-]{1,32})?") || key.isBlank() || key.indexOf(0) >= 0 || key.contains(":") || key.contains("\\")) {
            throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        }
        Path relative = Path.of(key);
        if (relative.isAbsolute()) throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        for (Path part : relative) {
            if (part.toString().equals(".") || part.toString().equals("..")) {
                throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
            }
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        Path cursor = target.getRoot();
        for (Path part : target) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new BusinessException(ProjectErrorCode.ATTACHMENT_KEY_MISMATCH);
        return target;
    }
}
