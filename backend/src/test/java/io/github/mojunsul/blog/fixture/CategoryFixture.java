package io.github.mojunsul.blog.fixture;

import io.github.mojunsul.blog.domain.category.entity.Category;

public final class CategoryFixture {

	private CategoryFixture() {}

	public static Category category(String slug) {
		return Category.builder().name("카테고리").slug(slug).build();
	}
}
