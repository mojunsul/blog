package io.github.mojunsul.blog.global.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// 빈으로 등록하면 서블릿 필터로도 자동 등록돼 두 번 실행되므로 SecurityConfig에서 new로 만든다
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtProvider jwtProvider;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String token = firstAccessToken(request);
		if (token != null && !token.isBlank()) {
			try {
				String username = jwtProvider.parseUsername(token);
				SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken
					.authenticated(username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
			} catch (JwtException | IllegalArgumentException e) {
				// 인증 없이 통과: 보호된 경로는 인가 단계에서 401, 공개 경로는 그대로 열린다
			}
		}
		chain.doFilter(request, response);
	}

	// access_token을 Path=/api보다 긴 Path로 발급하지 않으므로, 여러 개가 오면 첫 값이 현재 발급분이다
	private static String firstAccessToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (CookieUtil.ACCESS_TOKEN.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
