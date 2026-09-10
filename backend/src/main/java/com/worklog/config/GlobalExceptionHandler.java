package com.worklog.config;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러 계층에서 던져진 예외를 {@link ApiError} 로 변환한다.
 *
 * <p>Security 필터 체인에서 발생하는 401/403 은 이 핸들러를 타지 않으므로
 * {@link SecurityConfig} 의 EntryPoint / AccessDeniedHandler 가 같은 형식으로 따로 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("요청 형식이 올바르지 않습니다.");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", e.getMessage()));
    }

    /**
     * 쿼리 파라미터 형식 오류 — 알 수 없는 enum 값(type=NOPE), 날짜 형식 오류 등.
     * 클라이언트 잘못이므로 500 이 아니라 400 이다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String allowed = "";
        Class<?> required = e.getRequiredType();
        if (required != null && required.isEnum()) {
            allowed = " 가능한 값: %s".formatted(java.util.Arrays.toString(required.getEnumConstants()));
        }
        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "INVALID_PARAMETER",
                        "%s 값이 올바르지 않습니다: %s.%s".formatted(e.getName(), e.getValue(), allowed)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError(
                        "MISSING_PARAMETER", "%s 파라미터가 필요합니다.".formatted(e.getParameterName())));
    }

    /**
     * 경로는 맞는데 메서드가 다른 경우. 500 "서버 오류" 로 뭉뚱그리면 화면과 서버의 규약이
     * 어긋난 것을 알아채기 어렵다. 무엇을 받는지 그대로 알려 준다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        log.warn("허용되지 않은 메서드: {} (가능: {})", e.getMethod(), e.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError(
                        "METHOD_NOT_ALLOWED",
                        "%s 로는 부를 수 없는 경로입니다.".formatted(e.getMethod())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", "요청한 경로가 없습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "서버 오류가 발생했습니다."));
    }
}
