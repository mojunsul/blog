package io.github.mojunsul.blog.fixture;

import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.tag.entity.PostTag;
import io.github.mojunsul.blog.domain.tag.entity.Tag;

public final class PostTagFixture {

	private PostTagFixture() {}

	public static PostTag postTag(Post post, Tag tag) {
		return PostTag.builder().post(post).tag(tag).build();
	}
}
