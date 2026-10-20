package io.github.mojunsul.blog.global.exception;

import java.net.URI;
import java.util.List;

import org.springframework.http.ProblemDetail;

/** 9장 형식(ProblemDetail + {@code code}, {@code errors})의 응답 본문을 만든다. */
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
