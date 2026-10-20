package io.github.mojunsul.blog.global.exception;

import java.io.IOException;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/** 컨트롤러 밖(Security 필터 등)에서는 {@code GlobalExceptionHandler}가 닿지 않아 응답에 직접 쓴다. */
@Component
@RequiredArgsConstructor
public class ProblemDetailResponseWriter {

	private final JsonMapper jsonMapper;

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(),
			ProblemDetailFactory.of(code, request.getRequestURI(), List.of()));
	}
}
