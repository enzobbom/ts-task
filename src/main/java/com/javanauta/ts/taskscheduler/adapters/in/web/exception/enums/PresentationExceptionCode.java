package com.javanauta.ts.taskscheduler.adapters.in.web.exception.enums;

import com.javanauta.ts.taskscheduler.shared.exception.ExceptionCode;

public enum PresentationExceptionCode implements ExceptionCode {
    REQUEST_BODY_VIOLATION_ERROR("Request body validation failed. One or more fields did not meet the validation criteria"),
    PARAM_OR_PATH_VAR_VIOLATION_ERROR("Request parameter or path variable validation failed. One or more fields did not meet the validation criteria"),
    TYPE_MISMATCH_ERROR("Request parameter type mismatch. One or more fields could not be converted to the expected type"),
    JSON_PARSE_ERROR("Invalid JSON format or invalid data types. The request body could not be parsed"),
    INTERNAL_SERVER_ERROR("Internal server error");

    private final String defaultMessage;

    PresentationExceptionCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
