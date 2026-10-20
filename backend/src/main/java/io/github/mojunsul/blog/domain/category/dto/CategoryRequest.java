package io.github.mojunsul.blog.domain.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
	@NotBlank(message = "카테고리 이름을 입력하세요") @Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다") @Pattern(regexp = "^\\P{Cc}+$", message = "카테고리 이름에 제어 문자를 쓸 수 없습니다") String name,
	@NotBlank(message = "slug를 입력하세요") @Size(max = 100, message = "slug는 100자 이하여야 합니다") @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다") String slug) {

	// 검증·저장·응답이 모두 제거된 값을 쓰도록 검증 전에 제거한다
	public CategoryRequest {
		name = name == null ? null : name.strip();
	}
}
