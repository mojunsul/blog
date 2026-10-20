package io.github.mojunsul.blog.global.security;

import java.io.IOException;

import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;

import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.exception.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 기본 핸들러는 sendError(400)이라 /error의 ProblemDetail이 아닌 형식으로 나간다.
@Component
@RequiredArgsConstructor
public class ProblemDetailRequestRejectedHandler implements RequestRejectedHandler {

	private final ProblemDetailResponseWriter writer;

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
		RequestRejectedException requestRejectedException) throws IOException {
		writer.write(request, response, ErrorCode.INVALID_INPUT);
	}
}
