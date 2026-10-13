package io.github.mojunsul.blog.domain.post.dto;

import io.github.mojunsul.blog.domain.category.entity.Category;

public record PostCategoryResponse(String name, String slug) {

	public static PostCategoryResponse from(Category category) {
		return category == null ? null : new PostCategoryResponse(category.getName(), category.getSlug());
	}
}
