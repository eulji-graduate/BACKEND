package com.module06.backend.metering.application.port.out;

import java.util.List;

public interface StorageObjectDeletionPort {
    /** Validate every key first. Missing objects are idempotent; other failures must propagate. */
    void deleteAll(List<String> keys);
}
