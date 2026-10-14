package io.github.mojunsul.blog.fixture;

import io.github.mojunsul.blog.domain.category.entity.Category;

public final class CategoryFixture {

	private CategoryFixture() {}

	public static Category category(String slug) {
		return category("카테고리", slug);
	}

	public static Category category(String name, String slug) {
		return Category.builder().name(name).slug(slug).build();
	}
}
