package io.github.mojunsul.blog.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.mojunsul.blog.domain.auth.dto.LoginResult;
import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;
import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.domain.auth.repository.RefreshTokenRepository;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.fixture.RefreshTokenFixture;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.security.JwtProvider;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");
	private static final String DUMMY_HASH = "dummy-hash";

	@Mock
	AdminRepository adminRepository;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	JwtProvider jwtProvider;

	AuthService authService;

	@BeforeEach
	void setUp() {
		given(passwordEncoder.encode(any())).willReturn(DUMMY_HASH);
		authService = new AuthService(adminRepository, refreshTokenRepository, passwordEncoder, jwtProvider,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static String sha256Hex(String value) throws NoSuchAlgorithmException {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void 로그인하면_Refresh_토큰의_해시와_14일_뒤_만료_시각을_저장한다() throws Exception {
		// given
		Admin admin = AdminFixture.admin("admin");
		given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));
		given(passwordEncoder.matches("password", admin.getPassword())).willReturn(true);
		given(jwtProvider.createAccessToken("admin")).willReturn("access");

		// when
		LoginResult result = authService.login("admin", "password");

		// then
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		then(refreshTokenRepository).should().save(captor.capture());
		RefreshToken saved = captor.getValue();
		assertThat(result.username()).isEqualTo("admin");
		assertThat(result.accessToken()).isEqualTo("access");
		assertThat(result.refreshToken()).hasSize(43).isNotEqualTo(saved.getTokenHash());
		assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(result.refreshToken()));
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
		assertThat(saved.getAdmin()).isSameAs(admin);
	}

	@Test
	void 아이디가_없으면_더미_해시로_비교한_뒤_INVALID_CREDENTIALS다() {
		// given
		given(adminRepository.findByUsername("nobody")).willReturn(Optional.empty());
		given(passwordEncoder.matches("password", DUMMY_HASH)).willReturn(false);

		// when & then
		assertThatThrownBy(() -> authService.login("nobody", "password")).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
		then(passwordEncoder).should().matches("password", DUMMY_HASH);
		then(refreshTokenRepository).should(never()).save(any());
	}

	@Test
	void 비밀번호가_틀리면_INVALID_CREDENTIALS다() {
		// given
		Admin admin = AdminFixture.admin("admin");
		given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));
		given(passwordEncoder.matches("wrong", admin.getPassword())).willReturn(false);

		// when & then
		assertThatThrownBy(() -> authService.login("admin", "wrong")).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
		then(refreshTokenRepository).should(never()).save(any());
	}

	@Test
	void 비밀번호가_72바이트를_넘으면_matches가_참이어도_INVALID_CREDENTIALS다() {
		// given
		String longPassword = "가".repeat(25);
		Admin admin = AdminFixture.admin("admin");
		given(adminRepository.findByUsername("admin")).willReturn(Optional.of(admin));
		given(passwordEncoder.matches(longPassword, admin.getPassword())).willReturn(true);

		// when & then
		assertThat(longPassword.getBytes(StandardCharsets.UTF_8)).hasSizeGreaterThan(72);
		assertThatThrownBy(() -> authService.login("admin", longPassword)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
		then(passwordEncoder).should().matches(longPassword, admin.getPassword());
		then(refreshTokenRepository).should(never()).save(any());
	}

	private void assertInvalidRefreshToken(String refreshToken) {
		assertThatThrownBy(() -> authService.refresh(refreshToken)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	void 재발급하면_새_Access_토큰과_Refresh_토큰을_발급하고_새_해시를_저장한다() throws Exception {
		// given
		Admin admin = AdminFixture.admin("admin");
		String hash = sha256Hex("old-token");
		given(refreshTokenRepository.markUsed(hash, NOW)).willReturn(1);
		given(refreshTokenRepository.findByTokenHash(hash))
			.willReturn(Optional.of(RefreshTokenFixture.refreshToken(admin, hash, NOW.plusSeconds(60))));
		given(jwtProvider.createAccessToken("admin")).willReturn("access");

		// when
		LoginResult result = authService.refresh("old-token");

		// then
		ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
		then(refreshTokenRepository).should().save(captor.capture());
		RefreshToken saved = captor.getValue();
		assertThat(result.username()).isEqualTo("admin");
		assertThat(result.accessToken()).isEqualTo("access");
		assertThat(result.refreshToken()).hasSize(43).isNotEqualTo("old-token");
		assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(result.refreshToken()));
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
		assertThat(saved.getAdmin()).isSameAs(admin);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   "})
	void 재발급_쿠키가_없거나_비었으면_저장소를_거치지_않고_INVALID_REFRESH_TOKEN이다(String refreshToken) {
		// when & then
		assertInvalidRefreshToken(refreshToken);
		then(refreshTokenRepository).shouldHaveNoInteractions();
	}

	@Test
	void 없는_토큰으로_재발급하면_전체_삭제_없이_INVALID_REFRESH_TOKEN이다() throws Exception {
		// given
		String hash = sha256Hex("unknown");
		given(refreshTokenRepository.markUsed(hash, NOW)).willReturn(0);
		given(refreshTokenRepository.findByTokenHash(hash)).willReturn(Optional.empty());

		// when & then
		assertInvalidRefreshToken("unknown");
		then(refreshTokenRepository).should(never()).deleteByAdmin(any());
		then(refreshTokenRepository).should(never()).save(any());
	}

	@Test
	void 만료된_미사용_토큰으로_재발급하면_전체_삭제_없이_INVALID_REFRESH_TOKEN이다() throws Exception {
		// given
		String hash = sha256Hex("expired");
		given(refreshTokenRepository.markUsed(hash, NOW)).willReturn(0);
		given(refreshTokenRepository.findByTokenHash(hash))
			.willReturn(Optional.of(RefreshTokenFixture.refreshToken(AdminFixture.admin(), hash, NOW)));

		// when & then
		assertInvalidRefreshToken("expired");
		then(refreshTokenRepository).should(never()).deleteByAdmin(any());
	}

	@Test
	void 만료_전에_이미_쓴_토큰으로_재발급하면_관리자의_토큰을_모두_삭제하고_INVALID_REFRESH_TOKEN이다() throws Exception {
		// given
		Admin admin = AdminFixture.admin();
		String hash = sha256Hex("used");
		given(refreshTokenRepository.markUsed(hash, NOW)).willReturn(0);
		given(refreshTokenRepository.findByTokenHash(hash)).willReturn(Optional.of(
			RefreshTokenFixture.usedRefreshToken(admin, hash, NOW.plusSeconds(1), NOW.minusSeconds(10))));

		// when & then
		assertInvalidRefreshToken("used");
		then(refreshTokenRepository).should().deleteByAdmin(admin);
		then(refreshTokenRepository).should(never()).save(any());
	}

	@Test
	void 만료된_쓴_토큰으로_재발급하면_전체_삭제_없이_INVALID_REFRESH_TOKEN이다() throws Exception {
		// given
		String hash = sha256Hex("used-expired");
		given(refreshTokenRepository.markUsed(hash, NOW)).willReturn(0);
		given(refreshTokenRepository.findByTokenHash(hash)).willReturn(Optional.of(
			RefreshTokenFixture.usedRefreshToken(AdminFixture.admin(), hash, NOW, NOW.minusSeconds(10))));

		// when & then
		assertInvalidRefreshToken("used-expired");
		then(refreshTokenRepository).should(never()).deleteByAdmin(any());
	}

	@Test
	void 로그아웃하면_쿠키_값의_해시로_행을_삭제한다() throws Exception {
		// when
		authService.logout("token");

		// then
		then(refreshTokenRepository).should().deleteByTokenHash(sha256Hex("token"));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = {"", "   "})
	void 로그아웃_쿠키가_없거나_비었으면_저장소를_호출하지_않는다(String refreshToken) {
		// when
		authService.logout(refreshToken);

		// then
		then(refreshTokenRepository).shouldHaveNoInteractions();
	}
}
