package io.github.mojunsul.blog.global.exception;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;

/** 모든 에러를 ProblemDetail(RFC 9457) + 확장 필드 {@code code}, {@code errors}로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// 바인딩 실패의 기본 메시지는 클래스명이 담긴 프레임워크 내부 메시지라 노출하지 않는다
	private static final String BINDING_FAILURE_REASON = "형식이 올바르지 않습니다";

	public record FieldError(String field, String reason) {
	}

	@ExceptionHandler(BusinessException.class)
	ProblemDetail handleBusiness(BusinessException e, HttpServletRequest request) {
		log.warn("Business exception: code={}, path={}", e.errorCode().name(), request.getRequestURI());
		return problem(e.errorCode(), request.getRequestURI(), List.of());
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception on {}", request.getRequestURI(), e);
		return problem(ErrorCode.INTERNAL_ERROR, request.getRequestURI(), List.of());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = e.getBindingResult().getFieldErrors().stream()
			.map(f -> new FieldError(f.getField(),
				f.isBindingFailure() ? BINDING_FAILURE_REASON : f.getDefaultMessage()))
			.toList();
		return handleExceptionInternal(e, invalidInput(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
		HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldError> errors = e.getParameterValidationResults().stream()
			.flatMap(r -> r.getResolvableErrors().stream()
				.map(err -> new FieldError(r.getMethodParameter().getParameterName(),
					err.getDefaultMessage())))
			.toList();
		return handleExceptionInternal(e, invalidInput(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	/** 프레임워크가 만든 ProblemDetail(JSON 파싱 실패, 405 등)에도 {@code errors}와 400의 {@code code}를 채운다. */
	@Override
	protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
		HttpStatusCode statusCode, WebRequest request) {
		if (body instanceof ProblemDetail pd) {
			if (pd.getProperties() == null || !pd.getProperties().containsKey("errors")) {
				pd.setProperty("errors", List.of());
			}
			if (statusCode.value() == 400 && !pd.getProperties().containsKey("code")) {
				pd.setProperty("code", ErrorCode.INVALID_INPUT.name());
				pd.setDetail(ErrorCode.INVALID_INPUT.message());
			}
		}
		return super.createResponseEntity(body, headers, statusCode, request);
	}

	private static ProblemDetail invalidInput(List<FieldError> errors) {
		return problem(ErrorCode.INVALID_INPUT, null, errors);
	}

	private static ProblemDetail problem(ErrorCode code, String instance, List<FieldError> errors) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), code.message());
		if (instance != null) {
			pd.setInstance(java.net.URI.create(instance));
		}
		pd.setProperty("code", code.name());
		pd.setProperty("errors", errors);
		return pd;
	}
}
