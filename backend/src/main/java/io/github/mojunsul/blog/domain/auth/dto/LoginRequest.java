package io.github.mojunsul.blog.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record LoginRequest(
	@NotBlank(message = "아이디를 입력하세요") @Size(max = 50, message = "아이디는 50자 이하여야 합니다") String username,
	// 공백을 포함한 모든 문자를 비밀번호로 허용하므로 @NotBlank를 쓰지 않는다
	@NotEmpty(message = "비밀번호를 입력하세요") String password) {
}
