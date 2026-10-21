package io.github.mojunsul.blog.domain.post.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// 공개 목록의 PostSearchRequest에 status를 넣지 않는다: 공개 API가 DRAFT 조회 파라미터를 받는 길을 만들지 않기 위해서다.
public record AdminPostSearchRequest(
	@Min(value = 0, message = "page는 0 이상이어야 합니다") @Max(value = 9999, message = "page는 9999 이하여야 합니다") Integer page,
	@Min(value = 1, message = "size는 1 이상이어야 합니다") @Max(value = 50, message = "size는 50 이하여야 합니다") Integer size,
	PostStatus status) {

	public Pageable toPageable() {
		return PageRequest.of(page == null ? 0 : page, size == null ? 10 : size);
	}
}
