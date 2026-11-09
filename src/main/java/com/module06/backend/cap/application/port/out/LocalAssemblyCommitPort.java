package com.module06.backend.cap.application.port.out;

import com.module06.backend.cap.domain.model.Recording;

public interface LocalAssemblyCommitPort {
    /** Commit recording metadata and its storage usage in a single short transaction. */
    void cleanup(Long companyId, Long meetingId, java.util.List<Long> partIds);
    void commit(Long companyId, Recording recording, int lastSegment, int lastSeq);
}
