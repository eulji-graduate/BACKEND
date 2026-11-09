package com.module06.backend.cap.application.port.out;

import java.nio.file.Path;
import java.util.List;

public interface RecordingMediaAssemblyPort {
    Result assemble(Path workDirectory, List<SourcePart> parts);
    record SourcePart(int segment, int seq, Path file, String contentType) { }
    record Result(Path file, int durationSeconds) { }
}
