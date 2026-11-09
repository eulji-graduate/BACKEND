package com.module06.backend.cap.infrastructure.assembly;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.module06.backend.cap.application.port.out.LocalAssemblyCommitPort;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;

@Component
@Profile("local")
public class LocalAssemblyCommitAdapter implements LocalAssemblyCommitPort {
    private final JdbcTemplate jdbc;
    private final RecordingRepository recordings;
    public LocalAssemblyCommitAdapter(JdbcTemplate jdbc, RecordingRepository recordings) {
        this.jdbc = jdbc;
        this.recordings = recordings;
    }
    @Override
    @Transactional
    public void cleanup(Long companyId, Long meetingId, List<Long> partIds) {
        var meetings=jdbc.queryForList("SELECT id FROM meeting WHERE id=? AND company_id=? FOR UPDATE",Long.class,
                meetingId,companyId);
        if (meetings.isEmpty()) throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        for (Long id:partIds) jdbc.update("DELETE FROM recording_part WHERE id=? AND meeting_id=?",id,meetingId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM recording_part WHERE meeting_id=?",Long.class,meetingId)==0) {
            jdbc.update("DELETE FROM capture_upload_state WHERE meeting_id=?",meetingId);
        }
    }

    @Override
    @Transactional
    public void commit(Long companyId, Recording recording, int lastSegment, int lastSeq) {
        List<Long> projects = jdbc.queryForList(
                "SELECT project_id FROM meeting WHERE id=? AND company_id=? FOR UPDATE",
                Long.class, recording.getMeetingId(), companyId);
        if (projects.isEmpty()) throw new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND);
        var states = jdbc.queryForList("SELECT segment_seq,last_seq FROM capture_upload_state WHERE meeting_id=? FOR UPDATE",
                recording.getMeetingId());
        if (!states.isEmpty() && (((Number)states.get(0).get("segment_seq")).intValue()!=lastSegment
                || ((Number)states.get(0).get("last_seq")).intValue()!=lastSeq)) {
            throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
        }
        recordings.save(recording);
        // The usual metering writer uses REQUIRES_NEW. This local commit instead joins the metadata transaction.
        var rows = jdbc.queryForList("SELECT company_id, revision FROM meeting_storage_usage WHERE meeting_id=? FOR UPDATE",
                recording.getMeetingId());
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO meeting_storage_usage (meeting_id,company_id,project_id,used_bytes,revision,updated_at) "
                    + "VALUES (?,?,?,?,1,CURRENT_TIMESTAMP)", recording.getMeetingId(), companyId,
                    projects.get(0), recording.getSizeBytes());
        } else {
            if (((Number)rows.get(0).get("company_id")).longValue() != companyId.longValue()) {
                throw new BusinessException(CapErrorCode.CAP_RECORDING_KEY_MISMATCH);
            }
            long revision = Math.addExact(((Number)rows.get(0).get("revision")).longValue(),1);
            jdbc.update("UPDATE meeting_storage_usage SET project_id=?,used_bytes=?,revision=?,updated_at=CURRENT_TIMESTAMP "
                    + "WHERE meeting_id=? AND company_id=?", projects.get(0),recording.getSizeBytes(),revision,
                    recording.getMeetingId(),companyId);
        }
    }
}
