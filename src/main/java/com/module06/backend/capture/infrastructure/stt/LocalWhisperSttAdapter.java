package com.module06.backend.capture.infrastructure.stt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.module06.backend.cap.application.port.out.LocalRecordingFilePort;
import com.module06.backend.capture.application.port.out.SttJobPort;
import com.module06.backend.capture.application.port.out.SttJobResultPort;
import com.module06.backend.capture.domain.model.TranscriptSegmenter.Word;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The DB holds queued jobs across restarts. One native process pipeline runs at a time. */
@Slf4j
@Component
@Profile("local & !prod")
public class LocalWhisperSttAdapter implements SttJobPort, SttJobResultPort {
    private final LocalRecordingFilePort files;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String ffmpeg;
    private final String whisper;
    private final String model;
    private final long timeoutSeconds;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String activeJob;
    private Future<SttJobOutcome> activeResult;

    public LocalWhisperSttAdapter(LocalRecordingFilePort files, JdbcTemplate jdbc, ObjectMapper json,
            @Value("${app.local-assembly.ffmpeg:ffmpeg}") String ffmpeg,
            @Value("${capture.stt.local.executable:whisper-cli}") String whisper,
            @Value("${capture.stt.local.model:}") String model,
            @Value("${capture.stt.local.timeout-seconds:180}") long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("Local STT timeout must be positive");
        }
        this.files = files;
        this.jdbc = jdbc;
        this.json = json;
        this.ffmpeg = ffmpeg;
        this.whisper = whisper;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String submit(SttJob job) {
        // Poll only after the transaction that persists stt_block has committed.
        return job.providerJobName();
    }

    @Override
    public synchronized SttJobOutcome fetch(String jobName) {
        if (activeResult != null) {
            if (jobName.equals(activeJob)) {
                if (!activeResult.isDone()) {
                    return SttJobOutcome.of(State.RUNNING);
                }
                try {
                    return activeResult.get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return SttJobOutcome.of(State.UNAVAILABLE);
                } catch (ExecutionException error) {
                    log.error("Local STT worker failed for job {}", jobName, error);
                    return SttJobOutcome.failed("LOCAL_STT_FAILED");
                } finally {
                    activeJob = null;
                    activeResult = null;
                }
            }
            if (!activeResult.isDone()) {
                return SttJobOutcome.of(State.QUEUED);
            }
            // A completed job may leave the polling set before its result is collected.
            Integer active = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM stt_block
                    WHERE provider_job_name = ? AND status IN ('QUEUED', 'RUNNING')
                    """, Integer.class, activeJob);
            if (active != null && active > 0) {
                return SttJobOutcome.of(State.QUEUED);
            }
        }
        var inputs = jdbc.queryForList("""
                SELECT audio_s3_key FROM stt_block
                WHERE provider_job_name = ? AND status IN ('QUEUED', 'RUNNING')
                """, String.class, jobName);
        if (inputs.isEmpty()) {
            return SttJobOutcome.of(State.UNKNOWN);
        }
        String key = inputs.get(0);
        if (key == null || key.isBlank()) {
            return SttJobOutcome.failed("LOCAL_STT_AUDIO_MISSING");
        }
        activeJob = jobName;
        activeResult = worker.submit(() -> transcribe(jobName, key));
        return SttJobOutcome.of(State.RUNNING);
    }

    private SttJobOutcome transcribe(String jobName, String key) {
        if (model.isBlank() || !Files.isRegularFile(Path.of(model)) || !Files.isReadable(Path.of(model))) {
            log.warn("Local STT requires a multilingual Whisper model via LOCAL_WHISPER_MODEL");
            return SttJobOutcome.failed("LOCAL_STT_MODEL_MISSING");
        }

        Path work = null;
        try {
            Path input = files.find(key).toAbsolutePath();
            work = Files.createTempDirectory("backend-local-stt-");
            Path wav = work.resolve("audio.wav");
            Path result = work.resolve("transcript");
            run(List.of(ffmpeg, "-nostdin", "-v", "error", "-protocol_whitelist", "file",
                    "-f", inputFormat(input), "-i", input.toString(), "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le", wav.toString()));
            run(List.of(whisper, "-m", Path.of(model).toAbsolutePath().toString(), "-l", "ko",
                    "-f", wav.toString(), "-oj", "-of", result.toString()));
            var words = parseSegments(json.readTree(Files.readString(work.resolve("transcript.json"))));
            return words.isEmpty() ? SttJobOutcome.failed("LOCAL_STT_EMPTY") : SttJobOutcome.completed(words);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return SttJobOutcome.of(State.UNAVAILABLE);
        } catch (IOException | RuntimeException error) {
            log.warn("Local STT failed for job {}: {}", jobName, error.getMessage());
            return SttJobOutcome.failed("LOCAL_STT_FAILED");
        } finally {
            if (work != null && !FileSystemUtils.deleteRecursively(work.toFile())) {
                log.warn("Could not remove local STT temporary files for job {}", jobName);
            }
        }
    }

    private static String inputFormat(Path input) {
        // Limit autodetection to recording formats and exclude playlists that can open other files.
        String name = input.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "m4a", "mp4" -> "mov";
            case "webm" -> "matroska";
            case "wav", "mp3", "flac", "ogg", "amr" -> extension;
            default -> throw new IllegalArgumentException("Unsupported local STT audio format");
        };
    }

    private static List<Word> parseSegments(JsonNode json) {
        JsonNode segments = json.path("transcription");
        if (!segments.isArray()) {
            throw new IllegalArgumentException("Whisper output has no transcription array");
        }
        List<Word> words = new ArrayList<>();
        for (JsonNode segment : segments) {
            String text = segment.path("text").asString("").strip();
            if (text.isEmpty()) {
                continue;
            }
            int start = segment.path("offsets").path("from").asInt(-1);
            int end = segment.path("offsets").path("to").asInt(-1);
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("Whisper returned invalid segment offsets");
            }
            words.add(new Word(start, end, text, false, null));
        }
        return List.copyOf(words);
    }

    private void run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("Local STT process timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Local STT process exited with code " + process.exitValue());
            }
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
