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
	public static final String REFRESH_TOKEN = "refresh_token";

	public static final String POST_VIEWED = "post_viewed";

	private final AuthProperties properties;

	public ResponseCookie accessToken(String value) {
		return accessToken(value, JwtProvider.ACCESS_TOKEN_TTL);
	}

	public ResponseCookie refreshToken(String value) {
		return refreshToken(value, Duration.ofDays(14));
	}

	// 글마다 Path가 달라 쿠키가 따로 생기므로 글별로 24시간을 센다
	public ResponseCookie postViewed(String slug) {
		return cookie(POST_VIEWED, "1", "/api/v1/posts/" + slug + "/views", Duration.ofHours(24));
	}

	// 브라우저는 이름·Path가 같아야 지우므로 발급과 같은 코드를 거친다
	public ResponseCookie expiredAccessToken() {
		return accessToken("", Duration.ZERO);
	}

	public ResponseCookie expiredRefreshToken() {
		return refreshToken("", Duration.ZERO);
	}

	private ResponseCookie accessToken(String value, Duration maxAge) {
		return cookie(ACCESS_TOKEN, value, "/api", maxAge);
	}

	private ResponseCookie refreshToken(String value, Duration maxAge) {
		return cookie(REFRESH_TOKEN, value, "/api/v1/auth", maxAge);
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
