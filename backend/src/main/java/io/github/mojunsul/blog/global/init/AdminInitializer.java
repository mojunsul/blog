package io.github.mojunsul.blog.global.init;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.global.config.AdminProperties;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

	private static final String USERNAME_KEY = "blog.admin.username";
	private static final String PASSWORD_KEY = "blog.admin.password";
	private static final int MAX_USERNAME_LENGTH = 50;
	private static final int MIN_PASSWORD_LENGTH = 12;
	// BCrypt는 72바이트까지만 쓰고 Spring Security는 넘는 값을 거절하므로 기동 때 미리 막는다.
	private static final int MAX_PASSWORD_BYTES = 72;

	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;
	private final AdminProperties properties;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (adminRepository.count() > 0) {
			return;
		}

		boolean usernameBlank = properties.username().isBlank();
		boolean passwordBlank = properties.password().isBlank();
		if (usernameBlank && passwordBlank) {
			skipOrFail();
			return;
		}
		// 한쪽만 있으면 프로필과 무관하게 설정 실수로 보고 멈춘다.
		if (usernameBlank) {
			throw new IllegalStateException(USERNAME_KEY + " 설정이 필요합니다");
		}
		if (passwordBlank) {
			throw new IllegalStateException(PASSWORD_KEY + " 설정이 필요합니다");
		}

		validate(properties.username(), properties.password());
		adminRepository.save(Admin.builder()
			.username(properties.username())
			.password(passwordEncoder.encode(properties.password()))
			.build());
		log.info("Admin created: username={}", properties.username());
	}

	// 관리자 없이 운영 서버가 뜨면 로그인할 방법이 없고 설정 누락이 숨으므로 prod에서만 멈춘다.
	private void skipOrFail() {
		if (properties.required()) {
			throw new IllegalStateException(USERNAME_KEY + ", " + PASSWORD_KEY + " 설정이 필요합니다");
		}
		log.warn("Admin not created: {} and {} are not set", USERNAME_KEY, PASSWORD_KEY);
	}

	// 오류 메시지와 로그에 비밀번호 값을 넣지 않는다.
	private void validate(String username, String password) {
		if (username.length() > MAX_USERNAME_LENGTH) {
			throw new IllegalStateException(USERNAME_KEY + "는 " + MAX_USERNAME_LENGTH + "자 이하여야 합니다");
		}
		// 로그인 때 정확히 같은 값을 입력해야 하므로 공백을 몰래 지우지 않고 설정 오류로 본다.
		if (!username.equals(username.strip())) {
			throw new IllegalStateException(USERNAME_KEY + "는 앞뒤 공백이 없어야 합니다");
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(PASSWORD_KEY + "는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw new IllegalStateException(PASSWORD_KEY + "는 UTF-8 " + MAX_PASSWORD_BYTES + "바이트 이하여야 합니다");
		}
	}
}
