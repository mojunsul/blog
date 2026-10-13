package io.github.mojunsul.blog.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// M0 임시 설정: 헬스체크, API 문서, 공개 글 조회만 열어 둔다. JWT·CSRF 기반 본 설정은 M2에서 교체한다.
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.authorizeHttpRequests(auth -> auth
			.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
			.permitAll()
			.requestMatchers(HttpMethod.GET, "/api/v1/posts/**").permitAll()
			.anyRequest().authenticated());
		return http.build();
	}
}
