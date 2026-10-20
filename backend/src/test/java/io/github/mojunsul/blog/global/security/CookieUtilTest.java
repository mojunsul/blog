package io.github.mojunsul.blog.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseCookie;

import io.github.mojunsul.blog.global.config.AuthProperties;

class CookieUtilTest {

	private static CookieUtil cookieUtil(boolean secure) {
		return new CookieUtil(new AuthProperties("", false, secure));
	}

	@Test
	void access_token_쿠키는_Path_api에_900초다() {
		// when
		ResponseCookie cookie = cookieUtil(true).accessToken("value");

		// then
		assertThat(cookie.getName()).isEqualTo("access_token");
		assertThat(cookie.getValue()).isEqualTo("value");
		assertThat(cookie.getPath()).isEqualTo("/api");
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(900));
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Lax");
	}

	@Test
	void refresh_token_쿠키는_Path_api_v1_auth에_1209600초다() {
		// when
		ResponseCookie cookie = cookieUtil(true).refreshToken("value");

		// then
		assertThat(cookie.getName()).isEqualTo("refresh_token");
		assertThat(cookie.getValue()).isEqualTo("value");
		assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
		assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1209600));
		assertThat(cookie.isHttpOnly()).isTrue();
		assertThat(cookie.getSameSite()).isEqualTo("Lax");
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void Secure는_secure_cookie_설정을_따른다(boolean secure) {
		// given
		CookieUtil cookieUtil = cookieUtil(secure);

		// when & then
		assertThat(cookieUtil.accessToken("v").isSecure()).isEqualTo(secure);
		assertThat(cookieUtil.refreshToken("v").isSecure()).isEqualTo(secure);
	}
}
