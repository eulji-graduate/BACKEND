package com.module06.backend.cap.infrastructure.assembly;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.module06.backend.cap.application.port.out.LocalAssemblyCommitPort;
import com.module06.backend.cap.application.port.out.LocalRecordingFilePort;
import com.module06.backend.cap.application.port.out.RecordingAssemblyPort;
import com.module06.backend.cap.application.port.out.RecordingMediaAssemblyPort;
import com.module06.backend.cap.application.service.PendingAssemblyRetryRegistry;
import com.module06.backend.cap.domain.exception.CapErrorCode;
import com.module06.backend.cap.domain.model.CaptureUploadState;
import com.module06.backend.cap.domain.model.Recording;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.cap.domain.model.RecordingPartStatus;
import com.module06.backend.cap.domain.repository.MeetingReferenceRepository;
import com.module06.backend.cap.domain.repository.RecordingPartRepository;
import com.module06.backend.cap.domain.repository.RecordingRepository;
import com.module06.backend.global.exception.BusinessException;

/** Runs synchronously behind the existing async dispatcher; media work never holds a DB transaction. */
@Component
@Profile("local")
public class LocalRecordingAssemblyAdapter implements RecordingAssemblyPort {
    private static final Logger log=LoggerFactory.getLogger(LocalRecordingAssemblyAdapter.class);
    private final Set<Long> running=ConcurrentHashMap.newKeySet();
    private final LocalRecordingFilePort files;
    private final RecordingMediaAssemblyPort media;
    private final LocalAssemblyCommitPort commit;
    private final RecordingPartRepository parts;
    private final RecordingRepository recordings;
    private final MeetingReferenceRepository meetings;
    private final PendingAssemblyRetryRegistry retries;

    public LocalRecordingAssemblyAdapter(LocalRecordingFilePort files,RecordingMediaAssemblyPort media,
            LocalAssemblyCommitPort commit,RecordingPartRepository parts,RecordingRepository recordings,
            MeetingReferenceRepository meetings,PendingAssemblyRetryRegistry retries) {
        this.files=files; this.media=media; this.commit=commit; this.parts=parts; this.recordings=recordings;
        this.meetings=meetings; this.retries=retries;
    }

    @Override
    public void startAssembly(Long meetingId,int lastSegment,int lastSeq) {
        if (meetingId==null || !running.add(meetingId)) return;
        Path work=null;
        String published=null;
        boolean committed=false;
        try {
            if (lastSegment<0 || lastSeq<0 || lastSegment>CaptureUploadState.MAX_SEQ || lastSeq>CaptureUploadState.MAX_SEQ)
                throw new BusinessException(CapErrorCode.CAP_INVALID_SEQ);
            Long company=meetings.findCompanyId(meetingId).orElseThrow(() -> new BusinessException(CapErrorCode.CAP_MEETING_NOT_FOUND));
            var existing=recordings.findByMeetingId(meetingId);
            if (existing.isPresent()) {
                Recording recording=existing.get();
                if (recording.getFileUrl().startsWith("recordings/org-"+company+"/meeting-"+meetingId+"/assembly-")) {
                    if (!files.matches(recording.getFileUrl(),recording.getSizeBytes()))
                        throw new BusinessException(CapErrorCode.CAP_RECORDING_NOT_FOUND);
                    cleanup(meetingId,company,selected(parts.findAllByMeetingId(meetingId),lastSegment,lastSeq));
                }
                retries.clear(meetingId);
                return;
            }
            List<RecordingPart> snapshot=collect(meetingId,lastSegment,lastSeq);
            if (snapshot.isEmpty()) { retries.clear(meetingId); return; }
            List<RecordingMediaAssemblyPort.SourcePart> sources=new ArrayList<>();
            for (RecordingPart part:snapshot) {
                validateScope(company,meetingId,part);
                if (!files.matches(part.getS3Key(),part.getSizeBytes()))
                    throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
                sources.add(new RecordingMediaAssemblyPort.SourcePart(part.getSegmentSeq(),part.getSeq(),
                        files.find(part.getS3Key()),part.getContentType()));
            }
            work=Files.createTempDirectory("local-recording-assembly-").toRealPath();
            long estimate=Math.addExact(Math.multiplyExact(snapshot.stream().mapToLong(RecordingPart::getSizeBytes).sum(),64),
                    500L*1024*1024);
            if (Files.getFileStore(work).getUsableSpace()<estimate) throw new IllegalStateException("Insufficient temporary disk space");
            var output=media.assemble(work,sources);
            long size=Files.size(output.file());
            if (output.durationSeconds()<=0 || size<=0) throw new IllegalStateException("Invalid assembled recording");
            String key="recordings/org-"+company+"/meeting-"+meetingId+"/assembly-"+UUID.randomUUID()+".ogg";
            try (InputStream input=Files.newInputStream(output.file())) { files.store(key,input,size); }
            published=key;
            commit.commit(company,Recording.registerWithDuration(meetingId,"recording.ogg",key,size,
                    output.durationSeconds(),false),lastSegment,lastSeq);
            committed=true;
            cleanup(meetingId,company,snapshot);
            retries.clear(meetingId);
            log.info("Local recording assembly completed meetingId={} durationSeconds={} sizeBytes={}",meetingId,output.durationSeconds(),size);
        } catch (RuntimeException | IOException error) {
            if (!committed && published!=null) {
                try { files.delete(published); } catch (RuntimeException cleanupError) { error.addSuppressed(cleanupError); }
            }
            retries.recordFailure(meetingId,lastSegment,lastSeq);
            log.error("Local recording assembly failed; source metadata retained for retry meetingId={}",meetingId,error);
        } finally {
            if (work!=null) {
                try (var paths=Files.walk(work)) {
                    for (Path file:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
                } catch (IOException error) { log.warn("Assembly temporary directory cleanup failed",error); }
            }
            running.remove(meetingId);
        }
    }

    private List<RecordingPart> collect(Long meeting,int lastSegment,int lastSeq) {
        List<RecordingPart> all=parts.findAllByMeetingId(meeting).stream()
                .sorted(Comparator.comparingInt(RecordingPart::getSegmentSeq).thenComparingInt(RecordingPart::getSeq)).toList();
        if (all.stream().anyMatch(p -> p.getSegmentSeq()>lastSegment || (p.getSegmentSeq()==lastSegment && p.getSeq()>lastSeq)))
            throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
        var grouped=new TreeMap<Integer,List<RecordingPart>>();
        all.forEach(p -> grouped.computeIfAbsent(p.getSegmentSeq(),ignored -> new ArrayList<>()).add(p));
        if (lastSeq>0 && !grouped.containsKey(lastSegment)) throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
        for (var entry:grouped.entrySet()) {
            int expected=1;
            for (RecordingPart part:entry.getValue()) {
                if (part.getSeq()!=expected++ || (part.getStatus()!=RecordingPartStatus.UPLOADED && part.getStatus()!=RecordingPartStatus.ASSEMBLED))
                    throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
            }
            if (entry.getKey()==lastSegment && expected-1!=lastSeq) throw new BusinessException(CapErrorCode.CAP_ASSEMBLY_INCOMPLETE);
        }
        return all;
    }
    private List<RecordingPart> selected(List<RecordingPart> all,int lastSegment,int lastSeq) {
        return all.stream().filter(p -> p.getSegmentSeq()<lastSegment || (p.getSegmentSeq()==lastSegment && p.getSeq()<=lastSeq)).toList();
    }
    private void validateScope(Long company,Long meeting,RecordingPart part) {
        String prefix="stt-temp/org-"+company+"/meeting-"+meeting+"/segments/"+part.getSegmentSeq()+"/parts/";
        if (!meeting.equals(part.getMeetingId()) || part.getS3Key()==null || !part.getS3Key().startsWith(prefix)
                || part.getS3Key().contains("..")) throw new BusinessException(CapErrorCode.CAP_PART_KEY_MISMATCH);
    }
    private void cleanup(Long meeting,Long company,List<RecordingPart> snapshot) {
        for (RecordingPart part:snapshot) {
            validateScope(company,meeting,part);
            files.delete(part.getS3Key());
        }
        commit.cleanup(company,meeting,snapshot.stream().map(RecordingPart::getId).toList());
    }
}
