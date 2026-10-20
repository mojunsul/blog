package io.github.mojunsul.blog.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.auth.dto.LoginResult;
import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.entity.RefreshToken;
import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.domain.auth.repository.RefreshTokenRepository;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import io.github.mojunsul.blog.global.security.JwtProvider;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	// BCrypt는 72바이트 뒤를 잘라 비교하므로, 저장할 수 없는 길이(관리자 생성 때 금지)의 입력은 실패로 본다
	private static final int MAX_PASSWORD_BYTES = 72;
	private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
	private static final int REFRESH_TOKEN_BYTES = 32;

	private final AdminRepository adminRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();
	// 아이디가 없을 때도 BCrypt 비교를 해서 아이디 유무가 응답 시간으로 드러나지 않게 한다
	private final String dummyHash;

	public AuthService(AdminRepository adminRepository, RefreshTokenRepository refreshTokenRepository,
		PasswordEncoder passwordEncoder, JwtProvider jwtProvider, Clock clock) {
		this.adminRepository = adminRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtProvider = jwtProvider;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public LoginResult login(String username, String password) {
		Admin admin = adminRepository.findByUsername(username).orElse(null);
		boolean matched = passwordEncoder.matches(password, admin == null ? dummyHash : admin.getPassword());
		if (admin == null || !matched || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		String refreshToken = newRefreshToken();
		refreshTokenRepository.save(RefreshToken.builder()
			.admin(admin)
			.tokenHash(sha256Hex(refreshToken))
			.expiresAt(clock.instant().plus(REFRESH_TOKEN_TTL))
			.build());
		log.info("Login succeeded: username={}", username);
		return new LoginResult(admin.getUsername(), jwtProvider.createAccessToken(admin.getUsername()), refreshToken);
	}

	private String newRefreshToken() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String sha256Hex(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
		}
	}
}
