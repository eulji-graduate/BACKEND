package com.module06.backend.metering.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.module06.backend.global.exception.BusinessException;

class LocalStorageObjectDeletionAdapterTest {
    @TempDir Path root;

    @Test
    void deletesOnlyRequestedObjectsAndAllowsMissingFiles() throws Exception {
        Path file = root.resolve("recordings/org-1/meeting-2/audio.wav");
        Files.createDirectories(file.getParent());
        Files.writeString(file,"audio");
        Path other = root.resolve("keep.wav");
        Files.writeString(other,"keep");
        var adapter = new LocalStorageObjectDeletionAdapter(root.toString());
        adapter.deleteAll(List.of("recordings/org-1/meeting-2/audio.wav"));
        adapter.deleteAll(List.of("recordings/org-1/meeting-2/audio.wav"));
        assertThat(file).doesNotExist();
        assertThat(other).hasContent("keep");
    }

    @Test
    void validatesWholeBatchBeforeDeletingAndRejectsTraversalUrlsAndAbsolutePaths() throws Exception {
        Path file = root.resolve("keep.wav");
        Files.writeString(file,"keep");
        var adapter = new LocalStorageObjectDeletionAdapter(root.toString());
        for (String key : List.of("../outside", "/outside", "https://storage/file", "a/../../outside", ".", "a\\b")) {
            assertThatThrownBy(() -> adapter.deleteAll(List.of("keep.wav",key)))
                    .isInstanceOf(BusinessException.class);
            assertThat(file).exists();
        }
    }

    @Test
    void rejectsSymlinkedAncestorsWithoutDeletingTheirTargets() throws Exception {
        Path outside = Files.createDirectory(root.resolve("outside"));
        Path target = outside.resolve("audio.wav");
        Files.writeString(target,"keep");
        Path storage = Files.createDirectory(root.resolve("storage"));
        Files.createSymbolicLink(storage.resolve("link"),outside);
        var adapter = new LocalStorageObjectDeletionAdapter(storage.toString());
        assertThatThrownBy(() -> adapter.deleteAll(List.of("link/audio.wav")))
                .isInstanceOf(BusinessException.class);
        assertThat(target).hasContent("keep");
    }
}
