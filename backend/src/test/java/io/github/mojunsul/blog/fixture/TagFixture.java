package io.github.mojunsul.blog.fixture;

import io.github.mojunsul.blog.domain.tag.entity.Tag;

public final class TagFixture {

	private TagFixture() {}

	public static Tag tag(String name) {
		return Tag.builder().name(name).build();
	}
}
