package io.github.mojunsul.blog.global.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.exception.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	private final ProblemDetailResponseWriter writer;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		AccessDeniedException accessDeniedException) throws IOException {
		ErrorCode code = accessDeniedException instanceof CsrfException ? ErrorCode.INVALID_CSRF_TOKEN
			: ErrorCode.FORBIDDEN;
		writer.write(request, response, code);
	}
}
