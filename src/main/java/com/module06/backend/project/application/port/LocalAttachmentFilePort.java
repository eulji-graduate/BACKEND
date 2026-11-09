package com.module06.backend.project.application.port;

import java.io.InputStream;
import java.nio.file.Path;

public interface LocalAttachmentFilePort {
    void store(String key, InputStream input, long contentLength);
    Path find(String key);
    boolean matches(String key, long size);
    void delete(String key);
}
