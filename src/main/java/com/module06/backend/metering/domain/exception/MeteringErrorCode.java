package com.module06.backend.metering.domain.exception;

import com.module06.backend.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MeteringErrorCode implements ErrorCode {

    MT_RECORD_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-001", "Token usage record request is invalid."),
    MT_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "MT-002", "Company token plan is not configured."),
    MT_PERIOD_INVALID(HttpStatus.BAD_REQUEST, "MT-003", "Metering period must be formatted as YYYY-MM."),
    MT_FORBIDDEN_SCOPE(HttpStatus.FORBIDDEN, "MT-004", "Metering scope is not allowed."),
    MT_PLAN_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-005", "Company token plan request is invalid."),
    MT_STORAGE_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "MT-006", "Company storage plan is not configured."),
    MT_STORAGE_PLAN_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-007", "Company storage plan request is invalid."),
    MT_STORAGE_RECORD_COMMAND_INVALID(HttpStatus.BAD_REQUEST, "MT-008", "Storage usage report request is invalid."),
    MT_STORAGE_PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "MT-009", "Storage project not found."),
    MT_STORAGE_PROJECT_NOT_DONE(HttpStatus.CONFLICT, "MT-010", "Storage can be cleared only for a completed (DONE) project."),
    MT_STORAGE_PROCESSING(HttpStatus.CONFLICT, "MT-011", "녹음 또는 분석 처리 중인 프로젝트는 저장소를 삭제할 수 없습니다."),
    MT_STORAGE_OBJECT_INVALID(HttpStatus.CONFLICT, "MT-012", "삭제할 파일의 저장 경로를 확인할 수 없습니다."),
    MT_STORAGE_DELETE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "MT-013", "파일 삭제에 실패했습니다. 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
