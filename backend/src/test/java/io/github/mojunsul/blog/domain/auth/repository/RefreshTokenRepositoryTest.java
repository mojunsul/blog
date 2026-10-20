package io.github.mojunsul.blog.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.fixture.RefreshTokenFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;

class RefreshTokenRepositoryTest extends RepositoryTestSupport {

	private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

	@Autowired
	AdminRepository adminRepository;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Test
	void 같은_해시의_행을_사용_시각과_만료_시각까지_찾는다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin());
		refreshTokenRepository.save(
			RefreshTokenFixture.usedRefreshToken(admin, "hash-a", NOW.plusSeconds(60), NOW.minusSeconds(10)));

		// when
		RefreshToken found = refreshTokenRepository.findByTokenHash("hash-a").orElseThrow();

		// then
		assertThat(found.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(found.getUsedAt()).isEqualTo(NOW.minusSeconds(10));
	}

	@Test
	void 없는_해시는_찾지_못한다() {
		// when & then
		assertThat(refreshTokenRepository.findByTokenHash("nothing")).isEmpty();
	}

	@Test
	void 해시가_같은_행만_삭제하고_같은_관리자의_다른_토큰은_남긴다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin());
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "hash-a", NOW.plusSeconds(60)));
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "hash-b", NOW.plusSeconds(60)));

		// when
		refreshTokenRepository.deleteByTokenHash("hash-a");

		// then
		assertThat(refreshTokenRepository.findByTokenHash("hash-a")).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash("hash-b")).isPresent();
	}

	@Test
	void 없는_해시를_삭제해도_오류가_없다() {
		// when & then
		refreshTokenRepository.deleteByTokenHash("nothing");
	}

	@Test
	void 미사용이고_만료_전이면_사용_처리하고_1을_반환한다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin());
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "hash-a", NOW.plusSeconds(1)));

		// when
		int updated = refreshTokenRepository.markUsed("hash-a", NOW);

		// then
		assertThat(updated).isEqualTo(1);
		assertThat(refreshTokenRepository.findByTokenHash("hash-a").orElseThrow().getUsedAt()).isEqualTo(NOW);
	}

	@Test
	void 이미_사용한_토큰은_0을_반환하고_사용_시각을_바꾸지_않는다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin());
		Instant firstUsedAt = NOW.minusSeconds(10);
		refreshTokenRepository.save(
			RefreshTokenFixture.usedRefreshToken(admin, "hash-a", NOW.plusSeconds(60), firstUsedAt));

		// when
		int updated = refreshTokenRepository.markUsed("hash-a", NOW);

		// then
		assertThat(updated).isZero();
		assertThat(refreshTokenRepository.findByTokenHash("hash-a").orElseThrow().getUsedAt()).isEqualTo(firstUsedAt);
	}

	@Test
	void 만료_시각이_현재와_같으면_0을_반환한다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin());
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "hash-a", NOW));

		// when & then
		assertThat(refreshTokenRepository.markUsed("hash-a", NOW)).isZero();
	}

	@Test
	void 없는_해시는_0을_반환한다() {
		// when & then
		assertThat(refreshTokenRepository.markUsed("nothing", NOW)).isZero();
	}

	@Test
	void 만료_시각이_현재와_같은_행은_삭제하고_직후인_행은_남긴다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin());
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "expired", NOW));
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "past", NOW.minusSeconds(1)));
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "alive", NOW.plusSeconds(1)));

		// when
		refreshTokenRepository.deleteByExpiresAtLessThanEqual(NOW);

		// then
		assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash("past")).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash("alive")).isPresent();
	}

	@Test
	void 관리자의_토큰을_모두_삭제하고_다른_관리자의_토큰은_남긴다() {
		// given
		Admin admin = adminRepository.save(AdminFixture.admin("admin"));
		Admin other = adminRepository.save(AdminFixture.admin("other"));
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "hash-a", NOW.plusSeconds(60)));
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(admin, "hash-b", NOW.plusSeconds(60)));
		refreshTokenRepository.save(RefreshTokenFixture.refreshToken(other, "hash-c", NOW.plusSeconds(60)));

		// when
		refreshTokenRepository.deleteByAdmin(admin);

		// then
		assertThat(refreshTokenRepository.findByTokenHash("hash-a")).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash("hash-b")).isEmpty();
		assertThat(refreshTokenRepository.findByTokenHash("hash-c")).isPresent();
	}
}
