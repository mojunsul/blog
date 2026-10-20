package io.github.mojunsul.blog.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blog.auth")
public record AuthProperties(String jwtSecret, boolean jwtSecretRequired, boolean secureCookie) {

	// record가 만드는 toString은 jwtSecret 원문을 포함하므로, 객체를 로그나 예외 메시지에 넣어도 새지 않게 가린다
	@Override
	public String toString() {
		String maskedSecret = (jwtSecret == null || jwtSecret.isBlank()) ? "" : "****";
		return "AuthProperties[jwtSecret=" + maskedSecret + ", jwtSecretRequired=" + jwtSecretRequired
			+ ", secureCookie=" + secureCookie + "]";
	}
}
