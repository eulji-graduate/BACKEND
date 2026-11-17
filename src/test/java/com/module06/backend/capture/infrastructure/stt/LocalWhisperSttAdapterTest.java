package com.module06.backend.capture.infrastructure.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.module06.backend.cap.application.port.out.LocalRecordingFilePort;
import com.module06.backend.capture.application.port.out.SttJobResultPort.State;
import com.module06.backend.capture.application.port.out.SttJobResultPort.SttJobOutcome;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class LocalWhisperSttAdapterTest {
    @TempDir Path directory;

    private final LocalRecordingFilePort files = mock(LocalRecordingFilePort.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void convertsM4aAndReturnsKoreanTranscriptWithMillisecondOffsets() throws Exception {
        Path input = Files.createFile(directory.resolve("meeting.m4a"));
        Path model = Files.createFile(directory.resolve("ggml-base.bin"));
        Path ffmpeg = writeExecutable("ffmpeg", """
                [ "$6" = "-f" ] && [ "$7" = "mov" ] || exit 1
                for last do :; done
                touch "$last"
                """);
        Path whisper = writeExecutable("whisper-cli", """
                [ "$3" = "-l" ] && [ "$4" = "ko" ] && [ -f "$6" ] || exit 1
                for last do :; done
                cat > "$last.json" <<'JSON'
                {"transcription":[{"text":" 회의 시작합니다. ","offsets":{"from":100,"to":1800}}]}
                JSON
                """);
        when(files.find("recordings/meeting.m4a")).thenReturn(input);
        var adapter = adapter(ffmpeg.toString(), whisper.toString(), model.toString());
        try {
            var outcome = awaitOutcome(adapter);
            assertThat(outcome.state()).isEqualTo(State.COMPLETED);
            assertThat(outcome.words()).containsExactly(new Word(100, 1800, "회의 시작합니다.", false, null));
        } finally {
            adapter.shutdown();
        }
    }

    @Test
    void missingModelExitsQueueWithAnExplicitFailure() throws Exception {
        var adapter = adapter("ffmpeg", "whisper-cli", "");
        try {
            var outcome = awaitOutcome(adapter);
            assertThat(outcome.state()).isEqualTo(State.FAILED);
            assertThat(outcome.errorCode()).isEqualTo("LOCAL_STT_MODEL_MISSING");
        } finally {
            adapter.shutdown();
        }
    }

    private LocalWhisperSttAdapter adapter(String ffmpeg, String whisper, String model) {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("meeting-job")))
                .thenReturn(List.of("recordings/meeting.m4a"));
        return new LocalWhisperSttAdapter(files, jdbc, new ObjectMapper(), ffmpeg, whisper, model, 5);
    }

    private SttJobOutcome awaitOutcome(LocalWhisperSttAdapter adapter) throws InterruptedException {
        assertThat(adapter.fetch("meeting-job").state()).isEqualTo(State.RUNNING);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var outcome = adapter.fetch("meeting-job");
            if (outcome.state() != State.RUNNING) {
                return outcome;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Local STT worker did not finish");
    }

    private Path writeExecutable(String name, String script) throws Exception {
        Path path = Files.writeString(directory.resolve(name), "#!/bin/sh\n" + script);
        assertThat(path.toFile().setExecutable(true)).isTrue();
        return path;
    }
}
