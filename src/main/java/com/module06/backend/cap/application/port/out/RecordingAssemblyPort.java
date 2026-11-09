package com.module06.backend.cap.application.port.out;

/* comment.
    CAP과 조립 파이프라인 사이의 경계. seq 연속성 검증을 통과한 녹음의 조립을 트리거만 한다.
    local은 호스트 파일·ffmpeg, prod는 S3·ffmpeg 어댑터가 조립·재생 시간 측정·녹음 등록·청크 정리를 맡는다.
    기본/테스트 프로파일은 스텁을 사용한다. 조립 완료 후 상태 전이는 이 파이프라인이 책임진다.
*/
public interface RecordingAssemblyPort {

    /** 녹음 조립 파이프라인을 트리거한다(best-effort). lastSegmentSeq/lastSeq는 조립 범위 기준. */
    void startAssembly(Long meetingId, int lastSegmentSeq, int lastSeq);
}
