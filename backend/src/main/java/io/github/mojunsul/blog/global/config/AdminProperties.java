package io.github.mojunsul.blog.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blog.admin")
public record AdminProperties(String username, String password, boolean required) {

	// record가 만드는 toString은 password 원문을 포함하므로, 객체를 로그나 예외 메시지에 넣어도 새지 않게 가린다
	@Override
	public String toString() {
		String maskedPassword = (password == null || password.isBlank()) ? "" : "****";
		return "AdminProperties[username=" + username + ", password=" + maskedPassword + ", required=" + required + "]";
	}
}
