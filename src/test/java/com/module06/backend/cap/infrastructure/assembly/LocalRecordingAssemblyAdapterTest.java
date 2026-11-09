package com.module06.backend.cap.infrastructure.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.module06.backend.cap.application.port.out.*;
import com.module06.backend.cap.application.service.PendingAssemblyRetryRegistry;
import com.module06.backend.cap.domain.model.*;
import com.module06.backend.cap.domain.repository.*;
import com.module06.backend.cap.infrastructure.storage.LocalRecordingFileAdapter;

class LocalRecordingAssemblyAdapterTest {
    @TempDir Path root;
    LocalRecordingFileAdapter files;
    RecordingMediaAssemblyPort media;
    LocalAssemblyCommitPort commit;
    RecordingPartRepository parts;
    RecordingRepository recordings;
    MeetingReferenceRepository meetings;
    PendingAssemblyRetryRegistry retries;
    LocalRecordingAssemblyAdapter adapter;
    final List<RecordingPart> rows=new ArrayList<>();
    final AtomicReference<Recording> result=new AtomicReference<>();

    @BeforeEach
    void setup() {
        files=spy(new LocalRecordingFileAdapter(root.toString(),1024));
        media=mock(RecordingMediaAssemblyPort.class); commit=mock(LocalAssemblyCommitPort.class);
        parts=mock(RecordingPartRepository.class); recordings=mock(RecordingRepository.class);
        meetings=mock(MeetingReferenceRepository.class);
        retries=new PendingAssemblyRetryRegistry();
        adapter=new LocalRecordingAssemblyAdapter(files,media,commit,parts,recordings,meetings,retries);
        when(meetings.findCompanyId(10L)).thenReturn(Optional.of(1L));
        when(parts.findAllByMeetingId(10L)).thenAnswer(call -> List.copyOf(rows));
        when(recordings.findByMeetingId(10L)).thenAnswer(call -> Optional.ofNullable(result.get()));
        doAnswer(call -> { result.set(call.getArgument(1)); return null; }).when(commit).commit(anyLong(),any(),anyInt(),anyInt());
        doAnswer(call -> { List<Long> ids=call.getArgument(2); rows.removeIf(p -> ids.contains(p.getId())); return null; })
                .when(commit).cleanup(anyLong(),anyLong(),anyList());
        when(media.assemble(any(),anyList())).thenAnswer(call -> {
            Path output=((Path)call.getArgument(0)).resolve("recording.ogg");
            Files.write(output,new byte[]{1,2,3,4});
            return new RecordingMediaAssemblyPort.Result(output,2);
        });
    }
    private RecordingPart add(int segment,int seq) {
        String key="stt-temp/org-1/meeting-10/segments/"+segment+"/parts/"+seq+".webm";
        files.store(key,new ByteArrayInputStream(new byte[]{1,2,3,4}),4);
        RecordingPart part=RecordingPart.restore((long)(segment*100+seq),10L,segment,seq,key,"audio/webm",4,
                11L,RecordingPartStatus.UPLOADED,null,null,null);
        rows.add(part); return part;
    }

    @Test
    void assemblesOrderedSnapshotCommitsBeforeCleanupAndIsIdempotent() {
        var later=add(1,1); var second=add(0,2); var first=add(0,1);
        adapter.startAssembly(10L,1,1);
        assertThat(result.get().getDurationSec()).isEqualTo(2);
        assertThat(files.matches(result.get().getFileUrl(),4)).isTrue();
        assertThat(result.get().isSttTriggered()).isFalse();
        assertThat(rows).isEmpty();
        for (var part:List.of(first,second,later)) assertThat(root.resolve(part.getS3Key())).doesNotExist();
        assertThat(retries.snapshot()).isEmpty();
        verify(commit).cleanup(eq(1L),eq(10L),anyList());
        var order=inOrder(commit);
        order.verify(commit).commit(eq(1L),any(),eq(1),eq(1));
        order.verify(commit).cleanup(eq(1L),eq(10L),anyList());
        adapter.startAssembly(10L,1,1);
        verify(media,times(1)).assemble(any(),anyList());
        verify(commit,times(1)).commit(anyLong(),any(),anyInt(),anyInt());
    }

    @Test
    void missingSequenceOrExtraUploadIsRejectedWithoutTouchingSources() {
        var first=add(0,1); var third=add(0,3);
        adapter.startAssembly(10L,0,3);
        assertThat(result.get()).isNull();
        assertThat(rows).hasSize(2);
        assertThat(files.matches(first.getS3Key(),4)).isTrue();
        assertThat(files.matches(third.getS3Key(),4)).isTrue();
        verifyNoInteractions(media,commit);
        assertThat(retries.snapshot().get(10L).attempts()).isEqualTo(1);
        adapter.startAssembly(10L,0,1);
        verifyNoInteractions(media,commit);
    }

    @Test
    void missingFileAndCodecFailureRetainOriginalMetadataForRetry() {
        var first=add(0,1);
        files.delete(first.getS3Key());
        adapter.startAssembly(10L,0,1);
        verifyNoInteractions(media,commit);
        files.store(first.getS3Key(),new ByteArrayInputStream(new byte[]{1,2,3,4}),4);
        doThrow(new IllegalStateException("ffmpeg unavailable")).when(media).assemble(any(),anyList());
        adapter.startAssembly(10L,0,1);
        assertThat(result.get()).isNull(); assertThat(rows).hasSize(1);
        assertThat(files.matches(first.getS3Key(),4)).isTrue();
        verifyNoInteractions(commit);
        assertThat(retries.snapshot().get(10L).attempts()).isEqualTo(2);
    }

    @Test
    void commitFailureCompensatesOutputAndRetryCanSucceed() throws Exception {
        var first=add(0,1);
        doThrow(new IllegalStateException("DB failure")).when(commit).commit(anyLong(),any(),anyInt(),anyInt());
        adapter.startAssembly(10L,0,1);
        assertThat(result.get()).isNull();
        assertThat(files.matches(first.getS3Key(),4)).isTrue();
        try (var entries=Files.walk(root)) {
            assertThat(entries.filter(p -> p.getFileName().toString().startsWith("assembly-")).count()).isZero();
        }
        doAnswer(call -> { result.set(call.getArgument(1)); return null; }).when(commit).commit(anyLong(),any(),anyInt(),anyInt());
        adapter.startAssembly(10L,0,1);
        assertThat(result.get()).isNotNull();
        assertThat(rows).isEmpty();
        assertThat(retries.snapshot()).isEmpty();
    }

    @Test
    void cleanupFailureRetriesWithoutReencodingOrDeletingCommittedRecording() {
        var first=add(0,1);
        doThrow(new IllegalStateException("delete failed")).when(files).delete(first.getS3Key());
        adapter.startAssembly(10L,0,1);
        assertThat(result.get()).isNotNull();
        assertThat(files.matches(result.get().getFileUrl(),4)).isTrue();
        assertThat(rows).hasSize(1);
        doCallRealMethod().when(files).delete(first.getS3Key());
        adapter.startAssembly(10L,0,1);
        verify(media,times(1)).assemble(any(),anyList());
        verify(commit,times(1)).commit(anyLong(),any(),anyInt(),anyInt());
        assertThat(rows).isEmpty(); assertThat(retries.snapshot()).isEmpty();
    }

    @Test
    void concurrentDuplicateDispatchDoesNotStartAnotherEncoder() throws Exception {
        add(0,1);
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown(); release.await(5,java.util.concurrent.TimeUnit.SECONDS);
            Path out=((Path)call.getArgument(0)).resolve("recording.ogg");
            Files.write(out,new byte[]{1,2,3,4}); return new RecordingMediaAssemblyPort.Result(out,2);
        }).when(media).assemble(any(),anyList());
        var worker=new Thread(() -> adapter.startAssembly(10L,0,1));
        worker.start();
        try {
            assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            adapter.startAssembly(10L,0,1);
        } finally { release.countDown(); worker.join(5000); }
        assertThat(worker.isAlive()).isFalse();
        verify(media,times(1)).assemble(any(),anyList());
    }
}
