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
}
