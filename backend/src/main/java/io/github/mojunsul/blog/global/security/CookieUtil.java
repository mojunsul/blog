package io.github.mojunsul.blog.global.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import io.github.mojunsul.blog.global.config.AuthProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CookieUtil {

	static final String ACCESS_TOKEN = "access_token";
	static final String REFRESH_TOKEN = "refresh_token";

	private final AuthProperties properties;

	public ResponseCookie accessToken(String value) {
		return cookie(ACCESS_TOKEN, value, "/api", JwtProvider.ACCESS_TOKEN_TTL);
	}

	public ResponseCookie refreshToken(String value) {
		return cookie(REFRESH_TOKEN, value, "/api/v1/auth", Duration.ofDays(14));
	}

	private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(properties.secureCookie())
			.sameSite("Lax")
			.path(path)
			.maxAge(maxAge)
			.build();
	}
}
