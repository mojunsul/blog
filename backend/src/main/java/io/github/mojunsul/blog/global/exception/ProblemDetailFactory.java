package io.github.mojunsul.blog.global.exception;

import java.net.URI;
import java.util.List;

import org.springframework.http.ProblemDetail;

/** 컨트롤러 응답과 Security 필터 응답의 형식이 갈라지지 않게 한 곳에서 만든다. */
public final class ProblemDetailFactory {

	public record FieldError(String field, String reason) {
	}

	private ProblemDetailFactory() {}

	public static ProblemDetail of(ErrorCode code, String instance, List<FieldError> errors) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status(), code.message());
		if (instance != null) {
			pd.setInstance(URI.create(instance));
		}
		pd.setProperty("code", code.name());
		pd.setProperty("errors", errors);
		return pd;
	}
}
