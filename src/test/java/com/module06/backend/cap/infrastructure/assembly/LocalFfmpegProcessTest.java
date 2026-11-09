package com.module06.backend.cap.infrastructure.assembly;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFfmpegProcessTest {
    @TempDir Path work;
    @Test
    void missingBinaryAndNonzeroExitAreExplicitFailures() {
        var media=new LocalFfmpegRecordingAssembler("ffmpeg","ffprobe",1);
        assertThatThrownBy(() -> media.run(work,List.of(work.resolve("missing-binary").toString()),1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> media.run(work,List.of("/bin/sh","-c","exit 7"),1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("failed");
    }
    @Test
    void hungProcessTimesOut() {
        var media=new LocalFfmpegRecordingAssembler("ffmpeg","ffprobe",1);
        assertThatThrownBy(() -> media.run(work,List.of("/bin/sh","-c","sleep 30"),1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("timed out");
    }
}
