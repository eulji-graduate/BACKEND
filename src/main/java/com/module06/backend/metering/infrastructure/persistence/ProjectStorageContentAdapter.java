package com.module06.backend.metering.infrastructure.persistence;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.port.out.ProjectStorageContentPort;
import com.module06.backend.metering.application.port.out.StorageObjectDeletionPort;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;

/** JDBC avoids duplicate JPA mappings for tables owned by capture/cap/meeting. */
@Repository
@Transactional
public class ProjectStorageContentAdapter implements ProjectStorageContentPort {
    private final JdbcTemplate jdbc;
    private final StorageObjectDeletionPort objects;

    public ProjectStorageContentAdapter(JdbcTemplate jdbc, StorageObjectDeletionPort objects) {
        this.jdbc = jdbc;
        this.objects = objects;
    }

    @Override
    public void deleteContents(Long companyId, Long projectId) {
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM project WHERE company_id = ? AND id = ? FOR UPDATE",
                String.class, companyId, projectId);
        if (statuses.isEmpty()) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_PROJECT_NOT_FOUND);
        }
        if (!statuses.get(0).equals("DONE")) {
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_PROJECT_NOT_DONE);
        }
        List<Long> meetings = jdbc.queryForList(
                "SELECT id FROM meeting WHERE company_id = ? AND project_id = ? ORDER BY id FOR UPDATE",
                Long.class, companyId, projectId);
        Set<String> keys = new LinkedHashSet<>();
        // Validate every meeting before deleting any object, including unmetered content.
        for (Long meeting : meetings) {
            assertNotProcessing(meeting);
            collectKeys(keys, "SELECT file_url FROM recording WHERE meeting_id = ?", companyId, meeting);
            collectKeys(keys, "SELECT s3_key FROM recording_part WHERE meeting_id = ?", companyId, meeting);
            collectKeys(keys, "SELECT audio_s3_key FROM stt_block WHERE meeting_id = ?", companyId, meeting);
        }
        objects.deleteAll(new ArrayList<>(keys));
        // Object failures leave all metadata/keys and ledgers intact. Already removed files are safe to retry.
        for (Long meeting : meetings) {
            // Keep dispatched actions and assignment history; detach references to the removed evidence/decisions.
            jdbc.update("UPDATE action SET evidence_transcript_id = NULL WHERE company_id = ? "
                    + "AND evidence_transcript_id IN (SELECT id FROM transcript_chunk WHERE meeting_id = ?)",
                    companyId, meeting);
            jdbc.update("UPDATE meeting_assignment_tuple SET meeting_decision_id = NULL, evidence_transcript_id = NULL "
                    + "WHERE company_id = ? AND meeting_id = ?", companyId, meeting);
            for (String table : List.of("meeting_decision", "meeting_summary", "analysis_layer_artifact",
                    "caption_chunk", "transcript_chunk", "stt_gap", "stt_block", "recording_part",
                    "recording", "capture_upload_state", "summary_job")) {
                // Table names are a fixed internal allowlist; meeting IDs are always bound parameters.
                jdbc.update("DELETE FROM " + table + " WHERE meeting_id = ?", meeting);
            }
        }
    }

    private void collectKeys(Set<String> keys, String query, Long companyId, Long meetingId) {
        for (String key : jdbc.queryForList(query, String.class, meetingId)) {
            if (key == null) continue;
            String scope = "org-" + companyId + "/meeting-" + meetingId + "/";
            if (!(key.startsWith("recordings/" + scope) || key.startsWith("stt-temp/" + scope))
                    || key.contains("..") || key.contains("\\") || key.contains(":")) {
                throw new BusinessException(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID);
            }
            keys.add(key);
        }
    }

    private void assertNotProcessing(Long meetingId) {
        boolean active = exists("SELECT 1 FROM capture_upload_state WHERE meeting_id = ? "
                + "AND blocks_formed > finalized_blocks_count", meetingId)
                || exists("SELECT 1 FROM recording_part WHERE meeting_id = ? AND status IN ('PRESIGNED','UPLOADED')", meetingId)
                || exists("SELECT 1 FROM meeting WHERE id = ? AND status = 'IN_PROGRESS'", meetingId)
                || exists("SELECT 1 FROM capture_session WHERE meeting_id = ? AND status IN ('ACTIVE','PAUSED')", meetingId)
                || exists("SELECT 1 FROM stt_block WHERE meeting_id = ? AND status IN ('PENDING','QUEUED','RUNNING')", meetingId)
                || exists("SELECT 1 FROM analysis_layer WHERE meeting_id = ? AND status IN ('PENDING','RUNNING')", meetingId)
                || exists("SELECT 1 FROM summary_job WHERE meeting_id = ? AND status IN ('PENDING','PROCESSING')", meetingId);
        if (active) throw new BusinessException(MeteringErrorCode.MT_STORAGE_PROCESSING);
    }

    private boolean exists(String query, Long meetingId) {
        return !jdbc.queryForList(query, Integer.class, meetingId).isEmpty();
    }
}
