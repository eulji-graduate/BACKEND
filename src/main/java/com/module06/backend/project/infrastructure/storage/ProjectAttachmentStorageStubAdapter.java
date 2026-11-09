package com.module06.backend.project.infrastructure.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.module06.backend.project.application.port.ProjectAttachmentStoragePort;

/* comment.
    기본/테스트 프로파일용 스텁. local은 실제 로컬 저장, prod는 S3 저장 구현을 사용한다.
    실제 파일 이동 없음: 가짜 URL 문자열을 만들어 돌려주고, 삭제는 항상 성공 처리한다.
    s3Key는 이미 ProjectAttachmentService가 회사·프로젝트 접두까지 붙여 넘긴다 — 여기서 더
    가공하지 않는다.
*/
@Component
@Profile("!prod & !local")
public class ProjectAttachmentStorageStubAdapter implements ProjectAttachmentStoragePort {

    @Override
    public IssuedUploadUrl issueUploadUrl(String s3Key, long fileSize) {
        return new IssuedUploadUrl(
                "https://stub-storage.local/upload/" + s3Key,
                s3Key
        );
    }

    @Override
    public IssuedDownloadUrl issueDownloadUrl(String s3Key) {
        return new IssuedDownloadUrl("https://stub-storage.local/download/" + s3Key, 300);
    }

    @Override
    public void deleteObject(String fileUrl) {
        // 스텁이라 아무것도 안 함 — 실제 F 어댑터가 붙으면 여기서 진짜 삭제 호출
    }
}
