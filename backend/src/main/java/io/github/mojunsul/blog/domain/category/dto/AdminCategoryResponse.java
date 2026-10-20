package io.github.mojunsul.blog.domain.category.dto;

import io.github.mojunsul.blog.domain.category.entity.Category;

public record AdminCategoryResponse(Long id, String name, String slug) {

	public static AdminCategoryResponse from(Category category) {
		return new AdminCategoryResponse(category.getId(), category.getName(), category.getSlug());
	}
}
