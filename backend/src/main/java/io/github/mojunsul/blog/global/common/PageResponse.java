package io.github.mojunsul.blog.global.common;

import java.util.List;

import org.springframework.data.domain.Page;

// Page를 그대로 직렬화하면 내부 구조(pageable, sort)가 응답에 섞이고 버전에 따라 바뀌므로, 고정된 형태로 바꿔 반환한다.
public record PageResponse<T>(
	List<T> content,
	int page,
	int size,
	long totalElements,
	int totalPages,
	boolean hasNext) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
			page.getContent(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages(),
			page.hasNext());
	}
}
