package com.module06.backend.project.application.port;

/** Attachment object storage. fileUrl is an object key, not a browser URL.
 * local uses the filesystem; prod uses S3; default/test use the stub.
 * The local implementation verifies uploaded bytes before metadata confirmation.
 * S3 keeps its existing behavior (no HEAD verification) until separately implemented.
 */
public interface ProjectAttachmentStoragePort {

    IssuedUploadUrl issueUploadUrl(String s3Key, long fileSize);

    IssuedDownloadUrl issueDownloadUrl(String s3Key);

    void deleteObject(String fileUrl);

    /** Local storage checks existence and actual size; other profiles retain their existing behavior. */
    default void verifyUploaded(String fileUrl, long fileSize) { }

    /** Preserve the original download filename when the storage implementation supports it. */
    default IssuedDownloadUrl issueDownloadUrl(String key, String fileName) {
        return issueDownloadUrl(key);
    }

    record IssuedUploadUrl(String uploadUrl, String fileUrl) {
    }

    record IssuedDownloadUrl(String downloadUrl, int expiresInSeconds) {
    }
}
