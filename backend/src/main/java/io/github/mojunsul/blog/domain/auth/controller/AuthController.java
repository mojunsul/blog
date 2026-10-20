package io.github.mojunsul.blog.domain.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mojunsul.blog.domain.auth.dto.AdminResponse;
import io.github.mojunsul.blog.domain.auth.dto.LoginRequest;
import io.github.mojunsul.blog.domain.auth.dto.LoginResult;
import io.github.mojunsul.blog.domain.auth.service.AuthService;
import io.github.mojunsul.blog.global.security.CookieUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;
	private final CookieUtil cookieUtil;

	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
		// CsrfToken은 지연 객체라 값을 읽어야 토큰이 만들어져 쿠키로 저장된다
		csrfToken.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/login")
	public ResponseEntity<AdminResponse> login(
		@Valid
		@RequestBody
		LoginRequest request) {
		LoginResult result = authService.login(request.username(), request.password());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookieUtil.accessToken(result.accessToken()).toString())
			.header(HttpHeaders.SET_COOKIE, cookieUtil.refreshToken(result.refreshToken()).toString())
			.body(new AdminResponse(result.username()));
	}

	@GetMapping("/me")
	public AdminResponse me(Authentication authentication) {
		return new AdminResponse(authentication.getName());
	}
}
