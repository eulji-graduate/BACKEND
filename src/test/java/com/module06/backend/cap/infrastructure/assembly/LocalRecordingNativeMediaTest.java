package com.module06.backend.cap.infrastructure.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;
import com.module06.backend.cap.application.port.out.*;
import com.module06.backend.cap.application.service.PendingAssemblyRetryRegistry;
import com.module06.backend.cap.domain.model.RecordingPart;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.global.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;

/** Explicit task: real ffmpeg/ffprobe are required, never silently skipped. */
@Tag("local-media")
@AutoConfigureMockMvc
class LocalRecordingNativeMediaTest extends LocalAssemblyCommitIntegrationTest {
    private static final Path ROOT=root();
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.local-storage.root",ROOT::toString);
    }
    @Autowired LocalRecordingFilePort files;
    @Autowired RecordingAssemblyPort assembly;
    @Autowired RecordingMediaAssemblyPort media;
    @Autowired PendingAssemblyRetryRegistry retries;
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    private final String ffmpeg=System.getenv().getOrDefault("LOCAL_FFMPEG_PATH","ffmpeg");
    private final String ffprobe=System.getenv().getOrDefault("LOCAL_FFPROBE_PATH","ffprobe");

    @BeforeEach void resetFiles() throws Exception {
        FileSystemUtils.deleteRecursively(ROOT); Files.createDirectories(ROOT);
    }
    @AfterAll static void deleteRoot() throws Exception { FileSystemUtils.deleteRecursively(ROOT); }

    @Test
    void realWebmRotationSplitChunksCommitPlaybackRangeAndDelete() throws Exception {
        byte[] first=sample("first.webm","0.6","libopus");
        byte[] second=sample("second.webm","0.7","libopus");
        var one=upload(0,1,Arrays.copyOfRange(first,0,first.length/2));
        var two=upload(0,2,Arrays.copyOfRange(first,first.length/2,first.length));
        var three=upload(0,3,second);
        jdbc.update("UPDATE capture_upload_state SET last_seq=3 WHERE meeting_id=10");
        assembly.startAssembly(10L,0,3);
        var recording=recordings.findByMeetingId(10L).orElseThrow();
        assertThat(recording.getDurationSec()).isEqualTo(2);
        Path finalFile=files.find(recording.getFileUrl());
        double seconds=Double.parseDouble(tool().run(ROOT,List.of(ffprobe,"-v","error","-show_entries","format=duration",
                "-of","default=noprint_wrappers=1:nokey=1",finalFile.toString()),30).trim());
        assertThat(seconds).isBetween(1.15,1.55);
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=10",Long.class))
                .isEqualTo(Files.size(finalFile));
        assertThat(parts.findAllByMeetingId(10L)).isEmpty();
        for (var part:List.of(one,two,three)) assertThat(ROOT.resolve(part.getS3Key())).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM capture_upload_state WHERE meeting_id=10",Integer.class)).isZero();
        assembly.startAssembly(10L,0,3);
        assertThat(recordings.findByMeetingId(10L).orElseThrow().getFileUrl()).isEqualTo(recording.getFileUrl());
        String auth="Bearer "+tokens.createAccessToken(new AuthPrincipal(11L,1L,"OWNER",false,null));
        String body=mvc.perform(get("/api/meetings/10/recordings/playback-url").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.durationMs").value(2000))
                .andReturn().getResponse().getContentAsString();
        String url=json.readTree(body).path("data").path("url").asString();
        mvc.perform(get(URI.create(url)).header("Range","bytes=0-3"))
                .andExpect(status().isPartialContent()).andExpect(content().bytes(new byte[]{'O','g','g','S'}));
        mvc.perform(delete("/api/meetings/10/recordings").param("confirm","true").header("Authorization",auth))
                .andExpect(status().isOk());
        assertThat(finalFile).doesNotExist();
        assertThat(recordings.findByMeetingId(10L)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT used_bytes FROM meeting_storage_usage WHERE meeting_id=10",Long.class)).isZero();
    }

    @Test
    void malformedActualMediaRetainsSourcesAndRetryCanUseRepairedInput() throws Exception {
        byte[] valid=sample("valid.webm","0.4","libopus");
        var part=upload(0,1,valid);
        Path input=files.find(part.getS3Key());
        Files.write(input,new byte[valid.length]);
        jdbc.update("UPDATE capture_upload_state SET last_seq=1 WHERE meeting_id=10");
        assembly.startAssembly(10L,0,1);
        assertThat(recordings.findByMeetingId(10L)).isEmpty();
        assertThat(parts.findAllByMeetingId(10L)).hasSize(1);
        assertThat(input).exists();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM meeting_storage_usage WHERE meeting_id=10",Integer.class)).isZero();
        assertThat(retries.snapshot()).containsKey(10L);
        Files.write(input,valid); // Privileged fixture repair; ordinary uploads still cannot overwrite files.
        assembly.startAssembly(10L,0,1);
        assertThat(recordings.findByMeetingId(10L)).isPresent();
        assertThat(retries.snapshot()).doesNotContainKey(10L);
    }

    @Test
    void actualMixedMp4AndWebmSegmentsAreCombined() throws Exception {
        sample("webm.webm","0.5","libopus"); sample("mp4.mp4","0.5","aac");
        Path work=Files.createDirectory(ROOT.resolve("mixed"));
        var result=media.assemble(work,List.of(new RecordingMediaAssemblyPort.SourcePart(0,1,ROOT.resolve("webm.webm"),"audio/webm"),
                new RecordingMediaAssemblyPort.SourcePart(1,1,ROOT.resolve("mp4.mp4"),"audio/mp4")));
        assertThat(result.durationSeconds()).isBetween(1,2);
        assertThat(Files.size(result.file())).isPositive();
    }

    private byte[] sample(String name,String duration,String codec) throws Exception {
        Path file=ROOT.resolve(name);
        tool().run(ROOT,List.of(ffmpeg,"-nostdin","-hide_banner","-loglevel","error","-y","-f","lavfi","-i",
                "sine=frequency=440:duration="+duration,"-c:a",codec,file.toString()),30);
        return Files.readAllBytes(file);
    }
    private RecordingPart upload(int segment,int seq,byte[] bytes) {
        String key="stt-temp/org-1/meeting-10/segments/"+segment+"/parts/"+seq+".webm";
        files.store(key,new ByteArrayInputStream(bytes),bytes.length);
        return parts.save(RecordingPart.create(10L,segment,seq,key,"audio/webm",bytes.length,11L));
    }
    private LocalFfmpegRecordingAssembler tool() { return new LocalFfmpegRecordingAssembler(ffmpeg,ffprobe,30); }
    private static Path root() {
        try { return Files.createTempDirectory("local-assembly-native-").toRealPath(); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
}
