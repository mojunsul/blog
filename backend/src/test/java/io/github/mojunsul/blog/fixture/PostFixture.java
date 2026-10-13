package io.github.mojunsul.blog.fixture;

import java.time.Instant;

import io.github.mojunsul.blog.domain.post.entity.Post;

public final class PostFixture {

	private PostFixture() {}

	public static Post draft(String slug) {
		return Post.builder().title("제목").slug(slug).content("본문").build();
	}

	public static Post published(String slug, Instant publishedAt) {
		Post post = draft(slug);
		post.publish(publishedAt);
		return post;
	}
}
