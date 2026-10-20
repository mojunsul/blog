package io.github.mojunsul.blog.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
	@NotBlank @Size(max = 50) @Pattern(regexp = "^\\P{Cc}+$") String name,
	@NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug) {

	// 검증·저장·응답이 모두 제거된 값을 쓰도록 검증 전에 제거한다
	public CategoryRequest {
		name = name == null ? null : name.strip();
	}
}
