package com.module06.backend.metering.application.port.out;

public interface ProjectStorageContentPort {
    /** Delete metered content before clearing ledgers. Failures must propagate for retry. */
    void deleteContents(Long companyId, Long projectId);
}
