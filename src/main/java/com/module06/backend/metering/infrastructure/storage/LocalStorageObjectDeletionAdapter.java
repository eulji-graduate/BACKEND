package com.module06.backend.metering.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.port.out.StorageObjectDeletionPort;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

@Component
@Profile("!prod")
public class LocalStorageObjectDeletionAdapter implements StorageObjectDeletionPort {
    private final Path root;

    public LocalStorageObjectDeletionAdapter(@Value("${app.local-storage.root:.local/storage}") String root) {
        Path configured = Path.of(root).toAbsolutePath().normalize();
        try {
            this.root = Files.exists(configured) ? configured.toRealPath() : configured;
        } catch (IOException error) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_DELETE_FAILED);
        }
    }

    @Override
    public void deleteAll(List<String> keys) {
        List<Path> files = keys.stream().map(this::resolve).toList();
        try {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException error) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_DELETE_FAILED);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank() || key.contains(":") || key.indexOf(0) >= 0 || key.contains("\\")) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
        }
        Path relative = Path.of(key);
        if (relative.isAbsolute()) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
        }
        for (Path part : relative) {
            if (part.toString().equals("..") || part.toString().equals(".")) {
                throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
            }
        }
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
        }
        // Reject symlinks in the root's ancestors as well as within the object path.
        Path cursor = file.getRoot();
        for (Path part : file) {
            cursor = cursor.resolve(part);
            if (Files.isSymbolicLink(cursor)) {
                throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
            }
        }
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
        }
        return file;
    }
}
