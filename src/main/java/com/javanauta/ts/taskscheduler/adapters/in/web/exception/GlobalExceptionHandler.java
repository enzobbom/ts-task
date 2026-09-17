package com.javanauta.ts.taskscheduler.adapters.in.web.exception;

import com.javanauta.ts.apicontract.response.ValidationErrorDetail;
import com.javanauta.ts.taskscheduler.application.exception.enums.ServiceExceptionCode;
import com.javanauta.ts.taskscheduler.domain.exception.enums.DomainExceptionCode;
import com.javanauta.ts.taskscheduler.adapters.in.web.exception.enums.PresentationExceptionCode;
import com.javanauta.ts.apicontract.response.ErrorResponse;
import com.javanauta.ts.taskscheduler.shared.exception.ApplicationException;
import com.javanauta.ts.taskscheduler.shared.exception.ExceptionCode;
import com.javanauta.ts.taskscheduler.shared.exception.enums.ValidationExceptionSourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final Map<ExceptionCode, HttpStatus> BUSINESS_CODE_HTTP_STATUS_MAP = Map.of(
            ServiceExceptionCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND,
            ServiceExceptionCode.NO_TASK_OWNERSHIP, HttpStatus.FORBIDDEN,
            DomainExceptionCode.DOMAIN_VALIDATION_ERROR, HttpStatus.UNPROCESSABLE_CONTENT);

    private static final Map<ExceptionCode, HttpStatus> PRESENTATION_CODE_HTTP_STATUS_MAP = Map.of(
            PresentationExceptionCode.REQUEST_BODY_VIOLATION_ERROR, HttpStatus.UNPROCESSABLE_CONTENT,
            PresentationExceptionCode.PARAM_OR_PATH_VAR_VIOLATION_ERROR, HttpStatus.UNPROCESSABLE_CONTENT,
            PresentationExceptionCode.TYPE_MISMATCH_ERROR, HttpStatus.BAD_REQUEST,
            PresentationExceptionCode.JSON_PARSE_ERROR, HttpStatus.BAD_REQUEST,
            PresentationExceptionCode.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);

    // Input validation exceptions: @Valid and @Validated

    // RequestBody DTO validations (annotated with @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        ExceptionCode exceptionCode = PresentationExceptionCode.REQUEST_BODY_VIOLATION_ERROR;
        HttpStatus httpCode = PRESENTATION_CODE_HTTP_STATUS_MAP.get(exceptionCode);
        String errorIdentifier = exceptionCode.getIdentifier();

        // Object-level validation errors

        String uri = request.getRequestURI();
        String resourceName = uri.substring(uri.lastIndexOf("/") + 1);

        List<ValidationErrorDetail> objErrors = ex.getGlobalErrors()
                .stream()
                .map(objError -> new ValidationErrorDetail(
                        ValidationExceptionSourceType.OBJECT.getIdentifier(),
                        resourceName,
                        objError.getDefaultMessage()))
                .toList();

        // Field validation errors

        BindingResult bindingResult = ex.getBindingResult();
        List<ValidationErrorDetail> fieldErrors = ex.getFieldErrors()
                .stream()
                .map(fieldError -> new ValidationErrorDetail(
                        ValidationExceptionSourceType.FIELD.getIdentifier(),
                        fieldError.getField(),
                        fieldError.getDefaultMessage()))
                .toList();

        // combine object-level and field-level validation errors
        List<ValidationErrorDetail> allValidationErrors = new ArrayList<>();
        allValidationErrors.addAll(objErrors);
        allValidationErrors.addAll(fieldErrors);

        ErrorResponse errorResponse = new ErrorResponse(
                httpCode.value(),
                errorIdentifier,
                exceptionCode.getDefaultMessage(),
                allValidationErrors);

        return ResponseEntity.status(httpCode).body(errorResponse);
    }

    // Endpoint parameters validation (validation annotation at RequestParam and PathVariable)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        PresentationExceptionCode exceptionCode = PresentationExceptionCode.PARAM_OR_PATH_VAR_VIOLATION_ERROR;
        HttpStatus httpCode = PRESENTATION_CODE_HTTP_STATUS_MAP.get(exceptionCode);
        String errorIdentifier = exceptionCode.getIdentifier();

        List<ValidationErrorDetail> constraintViolations = ex.getConstraintViolations()
                .stream()
                .map(paramError -> new ValidationErrorDetail(
                        ValidationExceptionSourceType.PARAMETER.getIdentifier(),
                        paramError.getPropertyPath().toString(),
                        paramError.getMessage()))
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(
                httpCode.value(),
                errorIdentifier,
                exceptionCode.getDefaultMessage(),
                constraintViolations);

        return ResponseEntity.status(httpCode).body(errorResponse);
    }

    // Input validation exceptions: type mismatches and missing parameters

    // Handles type mismatch errors, such as when a parameter cannot be converted to the expected type
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        ExceptionCode exceptionCode = PresentationExceptionCode.TYPE_MISMATCH_ERROR;
        HttpStatus httpCode = PRESENTATION_CODE_HTTP_STATUS_MAP.get(exceptionCode);
        String errorIdentifier = exceptionCode.getIdentifier();
        String message = "Invalid value for parameter: " + ex.getName();

        ErrorResponse errorResponse = new ErrorResponse(
                httpCode.value(),
                errorIdentifier,
                message,
                List.of());

        return ResponseEntity.status(httpCode).body(errorResponse);
    }

    // Handles JSON parsing errors, such as malformed JSON or type mismatches
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ExceptionCode exceptionCode = PresentationExceptionCode.JSON_PARSE_ERROR;
        HttpStatus httpCode = PRESENTATION_CODE_HTTP_STATUS_MAP.get(exceptionCode);
        String errorIdentifier = exceptionCode.getIdentifier();

        ErrorResponse errorResponse = new ErrorResponse(
                httpCode.value(),
                errorIdentifier,
                exceptionCode.getDefaultMessage(),
                List.of());

        return ResponseEntity.status(httpCode).body(errorResponse);
    }

    //

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(ApplicationException ex) {
        ExceptionCode exceptionCode = ex.getCode();
        HttpStatus httpCode = BUSINESS_CODE_HTTP_STATUS_MAP.get(exceptionCode);

        List<ValidationErrorDetail> validationErrors = ex.getValidationExceptionDetails()
                .stream()
                .map(validationException -> new ValidationErrorDetail(
                        validationException.code().getExceptionSourceType().getIdentifier(),
                        validationException.source(),
                        validationException.message()))
                .toList();

        ErrorResponse errorResponse = new ErrorResponse(
                httpCode.value(),
                exceptionCode.getIdentifier(),
                ex.getMessage(),
                validationErrors);

        return ResponseEntity.status(httpCode).body(errorResponse);
    }

    // Generic error handling

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);

        ExceptionCode exceptionCode = PresentationExceptionCode.INTERNAL_SERVER_ERROR;
        HttpStatus httpCode = PRESENTATION_CODE_HTTP_STATUS_MAP.get(exceptionCode);
        String errorIdentifier = exceptionCode.getIdentifier();

        ErrorResponse errorResponse = new ErrorResponse(
                httpCode.value(),
                errorIdentifier,
                exceptionCode.getDefaultMessage(),
                List.of());

        return ResponseEntity.status(httpCode).body(errorResponse);
    }
}
