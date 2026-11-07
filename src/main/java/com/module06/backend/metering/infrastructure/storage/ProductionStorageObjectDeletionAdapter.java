package com.module06.backend.metering.infrastructure.storage;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.module06.backend.cap.application.port.out.CapObjectStoragePort;
import com.module06.backend.metering.application.port.out.StorageObjectDeletionPort;

@Component
@Profile("prod")
public class ProductionStorageObjectDeletionAdapter implements StorageObjectDeletionPort {
    private final CapObjectStoragePort storage;
    public ProductionStorageObjectDeletionAdapter(CapObjectStoragePort storage) { this.storage = storage; }
    @Override
    public void deleteAll(List<String> keys) { keys.forEach(storage::deleteRecording); }
}
