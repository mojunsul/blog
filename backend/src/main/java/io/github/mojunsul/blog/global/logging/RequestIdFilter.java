package io.github.mojunsul.blog.global.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Security 필터보다 먼저 실행해야 인증 실패 로그에도 요청 ID가 찍힌다.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	static final String HEADER = "X-Request-Id";
	static final String MDC_KEY = "requestId";

	// 클라이언트가 보낸 값이 그대로 로그에 찍히므로, 줄바꿈 등을 막아 로그 위조를 방지한다.
	private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String requestId = resolve(request.getHeader(HEADER));
		MDC.put(MDC_KEY, requestId);
		response.setHeader(HEADER, requestId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	private static String resolve(String header) {
		if (header != null && VALID_ID.matcher(header).matches()) {
			return header;
		}
		return UUID.randomUUID().toString();
	}
}
