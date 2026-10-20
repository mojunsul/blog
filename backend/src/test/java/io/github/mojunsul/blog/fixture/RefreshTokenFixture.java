package io.github.mojunsul.blog.fixture;

import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;

public final class RefreshTokenFixture {

	private RefreshTokenFixture() {}

	public static RefreshToken refreshToken(Admin admin, String tokenHash, Instant expiresAt) {
		return RefreshToken.builder().admin(admin).tokenHash(tokenHash).expiresAt(expiresAt).build();
	}

	// used_at은 운영에서 조건부 UPDATE로만 채우므로 setter 대신 리플렉션으로 만든다
	public static RefreshToken usedRefreshToken(Admin admin, String tokenHash, Instant expiresAt, Instant usedAt) {
		RefreshToken token = refreshToken(admin, tokenHash, expiresAt);
		ReflectionTestUtils.setField(token, "usedAt", usedAt);
		return token;
	}
}
