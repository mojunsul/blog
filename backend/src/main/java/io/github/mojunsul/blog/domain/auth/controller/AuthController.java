package io.github.mojunsul.blog.domain.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	@GetMapping("/csrf")
	public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
		// CsrfToken은 지연 객체라 값을 읽어야 토큰이 만들어져 쿠키로 저장된다
		csrfToken.getToken();
		return ResponseEntity.noContent().build();
	}
}
