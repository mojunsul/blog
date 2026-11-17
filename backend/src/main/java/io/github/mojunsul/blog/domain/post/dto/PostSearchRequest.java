package io.github.mojunsul.blog.domain.post.dto;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.github.mojunsul.blog.domain.tag.entity.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record PostSearchRequest(
	@Min(value = 0, message = "page는 0 이상이어야 합니다") @Max(value = 9999, message = "page는 9999 이하여야 합니다") Integer page,
	@Min(value = 1, message = "size는 1 이상이어야 합니다") @Max(value = 50, message = "size는 50 이하여야 합니다") Integer size,
	String category, String tag,
	@Size(max = 100, message = "검색어는 100자 이하여야 합니다") String q) {

	// 검증이 정리된 값을 보도록 생성자에서 정리한다. 빈 값은 필터 없음(null)이다.
	public PostSearchRequest {
		category = blankToNull(category == null ? null : category.strip());
		tag = blankToNull(tag == null ? null : Tag.normalize(tag));
		q = blankToNull(q == null ? null : q.strip());
	}

	public Pageable toPageable() {
		return PageRequest.of(page == null ? 0 : page, size == null ? 10 : size);
	}

	private static String blankToNull(String value) {
		return value == null || value.isEmpty() ? null : value;
	}
}
