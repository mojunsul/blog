package io.github.mojunsul.blog.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
	private final ProblemDetailAccessDeniedHandler accessDeniedHandler;
	private final JwtProvider jwtProvider;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
			.addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling(e -> e
				.authenticationEntryPoint(authenticationEntryPoint)
				.accessDeniedHandler(accessDeniedHandler))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/posts/**", "/api/v1/categories", "/api/v1/auth/csrf")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout")
				.permitAll()
				.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
				.anyRequest().authenticated());
		return http.build();
	}

	// spa()는 쿠키 속성을 바꿀 수 없어 저장소만 교체한다. Secure는 기본값(request.isSecure())을 따른다.
	private static CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
		return repository;
	}
}
