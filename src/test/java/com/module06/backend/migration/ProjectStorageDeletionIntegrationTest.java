package com.module06.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.metering.application.port.out.StorageObjectDeletionPort;
import com.module06.backend.metering.application.usecase.DeleteProjectStorageUseCase;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.infrastructure.storage.LocalStorageObjectDeletionAdapter;

@Tag("local-sql")
@Testcontainers
@Import(MigrationSchemaValidationTest.ExcludeArchitectureFixtureEntities.class)
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
@Sql(scripts = "/db/local/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Transactional
class ProjectStorageDeletionIntegrationTest {
    @Container @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.40")
            .withDatabaseName("storage_delete_test")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/local/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired DeleteProjectStorageUseCase deleteStorage;
    @MockitoBean StorageObjectDeletionPort objects;
    @TempDir Path root;
    private static final String AUDIO = "recordings/org-1/meeting-140/audio.wav";
    private static final String PART = "stt-temp/org-1/meeting-140/segments/0/parts/1.webm";
    private static final String BLOCK = "stt-temp/org-1/meeting-140/blocks/0.wav";

    @BeforeEach
    void seedContent() throws Exception {
        jdbc.update("INSERT INTO recording (meeting_id,file_name,file_url,file_size,stt_triggered) VALUES (140,'audio',?,4,TRUE)", AUDIO);
        jdbc.update("INSERT INTO recording_part (meeting_id,segment_seq,seq,s3_key,status,uploader_member_id,content_type) VALUES (140,0,1,?,'ASSEMBLED',2,'audio/webm')",PART);
        jdbc.update("INSERT INTO stt_block (meeting_id,block_seq,start_offset_ms,end_offset_ms,cut_reason,audio_s3_key,status) "
                + "VALUES (140,0,0,1000,'WHOLE_FILE',?,'DONE')",BLOCK);
        jdbc.update("INSERT INTO transcript_chunk (id,meeting_id,seq,content) VALUES (9001,140,1,'transcript')");
        jdbc.update("INSERT INTO caption_chunk (meeting_id,member_id,seq,start_offset_ms,end_offset_ms,text,rms) "
                + "VALUES (140,2,1,0,1000,'caption',-10)");
        jdbc.update("INSERT INTO meeting_summary (id,company_id,meeting_id,overview) VALUES (9002,1,140,'summary')");
        jdbc.update("INSERT INTO meeting_decision (id,meeting_summary_id,meeting_id,topic,content,item_type) "
                + "VALUES (9003,9002,140,'topic','decision','DECISION')");
        jdbc.update("INSERT INTO meeting_assignment_tuple (id,company_id,meeting_id,meeting_decision_id,topic,title,evidence_transcript_id,action_id) "
                + "VALUES (9004,1,140,9003,'topic','Action history',9001,9005)");
        jdbc.update("INSERT INTO `action` (id,company_id,project_id,source_meeting_id,action_type,title,due_date,evidence_transcript_id) "
                + "VALUES (9005,1,14,140,'PERSONAL','Preserved action',CURRENT_DATE(),9001)");
        jdbc.update("INSERT INTO analysis_layer_artifact (meeting_id,layer,payload) VALUES (140,'L1.5','{}')");
        // Another project's content must survive, including when ledgers are absent.
        jdbc.update("INSERT INTO transcript_chunk (id,meeting_id,seq,content) VALUES (9006,150,1,'keep')");
        jdbc.update("INSERT INTO transcript_chunk (id,meeting_id,seq,content) VALUES (9007,141,1,'unmetered')");
        jdbc.update("DELETE FROM meeting_storage_usage WHERE meeting_id=141");
        jdbc.update("DELETE FROM meeting_text_storage_usage WHERE meeting_id=141");
        for (String key : List.of(AUDIO,PART,BLOCK)) {
            Path file = root.resolve(key);
            Files.createDirectories(file.getParent());
            Files.writeString(file,"data");
        }
        var local = new LocalStorageObjectDeletionAdapter(root.toString());
        doAnswer(call -> { local.deleteAll(call.getArgument(0)); return null; }).when(objects).deleteAll(anyList());
    }

    @Test
    void removesFilesAndContentPreservesHistoryAndCanBeRepeated() {
        deleteStorage.deleteByTag(1L,"hr-process");
        deleteStorage.deleteByTag(1L,"hr-process");
        for (String key : List.of(AUDIO,PART,BLOCK)) assertThat(root.resolve(key)).doesNotExist();
        for (String table : List.of("recording","recording_part","stt_block","caption_chunk","transcript_chunk",
                "meeting_summary","meeting_decision","analysis_layer_artifact")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE meeting_id=140",Integer.class)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=140",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT caption_bytes+transcript_bytes+summary_bytes FROM meeting_text_storage_usage "
                + "WHERE meeting_id=140",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT title FROM `action` WHERE id=9005",String.class)).isEqualTo("Preserved action");
        assertThat(jdbc.queryForObject("SELECT evidence_transcript_id FROM `action` WHERE id=9005",Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meeting_assignment_tuple WHERE id=9004",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT meeting_decision_id FROM meeting_assignment_tuple WHERE id=9004",Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meeting WHERE id=140",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM project WHERE id=14",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT content FROM transcript_chunk WHERE id=9006",String.class)).isEqualTo("keep");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transcript_chunk WHERE id=9007",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=150",Long.class)).isPositive();
    }

    @Test
    void fileFailureKeepsMetadataAndUsageSoRetryCanFinish() {
        doThrow(new BusinessException(MeteringErrorCode.MT_STORAGE_DELETE_FAILED)).when(objects).deleteAll(anyList());
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L,"hr-process")).isInstanceOf(BusinessException.class);
        assertIntact();
    }

    @Test
    void laterFileFailureLeavesKeysAndAllowsRetryAfterPartialDeletion() throws Exception {
        doAnswer(call -> {
            Files.deleteIfExists(root.resolve(AUDIO));
            throw new BusinessException(MeteringErrorCode.MT_STORAGE_DELETE_FAILED);
        }).when(objects).deleteAll(anyList());
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L,"hr-process")).isInstanceOf(BusinessException.class);
        assertThat(root.resolve(AUDIO)).doesNotExist();
        assertIntact();
        var local = new LocalStorageObjectDeletionAdapter(root.toString());
        doAnswer(call -> { local.deleteAll(call.getArgument(0)); return null; }).when(objects).deleteAll(anyList());
        deleteStorage.deleteByTag(1L,"hr-process");
        entityManager.flush();
        assertThat(root.resolve(PART)).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recording WHERE meeting_id=140",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=140",Long.class)).isZero();
    }

    @Test
    void rejectsActiveProcessingInAnyMeetingBeforeDeletingFiles() {
        jdbc.update("INSERT INTO summary_job (meeting_id,status) VALUES (141,'PROCESSING')");
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L,"hr-process"))
                .isInstanceOfSatisfying(BusinessException.class,e ->
                        assertThat(e.getErrorCode()).isEqualTo(MeteringErrorCode.MT_STORAGE_PROCESSING));
        verifyNoInteractions(objects);
        assertIntact();
        assertThat(root.resolve(AUDIO)).exists();
    }

    @Test
    void wrongCompanyAndNonDoneProjectDoNotReachObjectDeletion() {
        assertThatThrownBy(() -> deleteStorage.deleteByTag(2L,"hr-process")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L,"product-v2")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(objects);
        assertIntact();
    }

    @Test
    void rejectsCrossMeetingObjectKeyBeforeDeletingAnyFiles() {
        jdbc.update("UPDATE recording SET file_url='recordings/org-2/meeting-140/audio.wav' WHERE meeting_id=140");
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L,"hr-process"))
                .isInstanceOfSatisfying(BusinessException.class,e ->
                        assertThat(e.getErrorCode()).isEqualTo(MeteringErrorCode.MT_STORAGE_OBJECT_INVALID));
        verifyNoInteractions(objects);
        assertIntact();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "UPDATE stt_block SET status='QUEUED' WHERE meeting_id=140",
        "UPDATE stt_block SET status='RUNNING' WHERE meeting_id=140",
        "UPDATE recording_part SET status='UPLOADED' WHERE meeting_id=140",
        "UPDATE meeting SET status='IN_PROGRESS' WHERE id=141",
        "INSERT INTO capture_upload_state (meeting_id,blocks_formed,finalized_blocks_count) VALUES (141,2,1)"
    })
    void pendingWritersPreventDeletion(String setup) {
        jdbc.update(setup);
        assertThatThrownBy(() -> deleteStorage.deleteByTag(1L,"hr-process"))
                .isInstanceOfSatisfying(BusinessException.class,e ->
                        assertThat(e.getErrorCode()).isEqualTo(MeteringErrorCode.MT_STORAGE_PROCESSING));
        verifyNoInteractions(objects);
        assertIntact();
    }

    private void assertIntact() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recording WHERE meeting_id=140",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transcript_chunk WHERE meeting_id=140",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=140",Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT summary_bytes FROM meeting_text_storage_usage WHERE meeting_id=140",Long.class)).isPositive();
    }
}
