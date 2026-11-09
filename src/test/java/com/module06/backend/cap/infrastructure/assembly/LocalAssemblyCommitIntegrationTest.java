package com.module06.backend.cap.infrastructure.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import com.module06.backend.cap.application.port.out.LocalAssemblyCommitPort;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:assembly_commit_${random.uuid};MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.data.redis.password=test","ai.internal-token=test"
})
@ActiveProfiles("local")
class LocalAssemblyCommitIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LocalAssemblyCommitPort commit;
    @Autowired RecordingRepository recordings;
    @Autowired RecordingPartRepository parts;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO company (id,code,name) VALUES (1,'ASSEMBLY','Demo')");
        jdbc.update("INSERT INTO role (id,name) VALUES (2,'None')");
        jdbc.update("INSERT INTO member (id,company_id,role_id,email,password_hash,name,authority,is_admin,status) "
                + "VALUES (11,1,2,'host@example.test','hash','Host','OWNER',FALSE,'ACTIVE')");
        jdbc.update("INSERT INTO project (id,company_id,tag,name,color,status,due_date,created_at,updated_at) "
                + "VALUES (20,1,'assembly','Assembly','#000000','DONE',CURRENT_DATE(),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int id:List.of(10,12)) jdbc.update("INSERT INTO meeting (id,company_id,project_id,host_member_id,title,status,is_online,recording_consent,created_at,updated_at) "
                + "VALUES (?,1,20,11,'Demo','DONE',FALSE,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id);
        jdbc.update("INSERT INTO capture_upload_state (meeting_id,segment_seq,recorder_person_id,last_seq,blocks_formed,"
                + "last_block_end_offset_ms,reserved_up_to_offset_ms,finalized_blocks_count,created_at,updated_at) VALUES (10,0,11,2,0,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }
    @AfterEach
    void cleanup() {
        for (String table:List.of("recording_part","recording","capture_upload_state","meeting_storage_usage","meeting",
                "project","member","role","company")) jdbc.update("DELETE FROM "+table);
    }
    private Recording recording() {
        return Recording.registerWithDuration(10L,"recording.ogg","recordings/org-1/meeting-10/assembly-test.ogg",456,3,false);
    }

    @Test
    void storesMetadataAndAdvancesExistingLedgerRevisionTogether() {
        jdbc.update("INSERT INTO meeting_storage_usage (meeting_id,company_id,project_id,used_bytes,revision,updated_at) "
                + "VALUES (10,1,20,0,2,CURRENT_TIMESTAMP)");
        commit.commit(1L,recording(),0,2);
        assertThat(recordings.findByMeetingId(10L).orElseThrow().getSizeBytes()).isEqualTo(456);
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=10",Long.class)).isEqualTo(456);
        assertThat(jdbc.queryForObject("SELECT revision FROM meeting_storage_usage WHERE meeting_id=10",Long.class)).isEqualTo(3);
        assertThatThrownBy(() -> commit.commit(1L,recording(),0,2)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT revision FROM meeting_storage_usage WHERE meeting_id=10",Long.class)).isEqualTo(3);
    }

    @Test
    void usageScopeFailureRollsBackAlreadyFlushedRecordingMetadata() {
        jdbc.update("INSERT INTO meeting_storage_usage (meeting_id,company_id,project_id,used_bytes,revision,updated_at) "
                + "VALUES (10,2,20,99,2,CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> commit.commit(1L,recording(),0,2)).isInstanceOf(BusinessException.class);
        assertThat(recordings.findByMeetingId(10L)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=10",Long.class)).isEqualTo(99);
    }

    @Test
    void changedTailAndOtherCompanyCannotCommit() {
        assertThatThrownBy(() -> commit.commit(1L,recording(),0,1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> commit.commit(2L,recording(),0,2)).isInstanceOf(BusinessException.class);
        assertThat(recordings.findByMeetingId(10L)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meeting_storage_usage",Integer.class)).isZero();
    }

    @Test
    void cleanupOnlyDeletesSnapshotIdsInSameMeetingAndKeepsLaterUploads() {
        var first=parts.save(RecordingPart.create(10L,0,1,"first","audio/webm",4,11L));
        var late=parts.save(RecordingPart.create(10L,0,2,"late","audio/webm",4,11L));
        var other=parts.save(RecordingPart.create(12L,0,1,"other","audio/webm",4,11L));
        commit.cleanup(1L,10L,List.of(first.getId(),other.getId()));
        assertThat(parts.findAllByMeetingId(10L)).extracting(RecordingPart::getId).containsExactly(late.getId());
        assertThat(parts.findAllByMeetingId(12L)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM capture_upload_state WHERE meeting_id=10",Integer.class)).isEqualTo(1);
        commit.cleanup(1L,10L,List.of(late.getId()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM capture_upload_state WHERE meeting_id=10",Integer.class)).isZero();
    }
}
