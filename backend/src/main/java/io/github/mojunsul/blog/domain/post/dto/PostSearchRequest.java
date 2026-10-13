package io.github.mojunsul.blog.domain.post.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PostSearchRequest(
	@Min(value = 0, message = "page는 0 이상이어야 합니다") Integer page,
	@Min(value = 1, message = "size는 1 이상이어야 합니다") @Max(value = 50, message = "size는 50 이하여야 합니다") Integer size) {

	public Pageable toPageable() {
		return PageRequest.of(page == null ? 0 : page, size == null ? 10 : size);
	}
}
