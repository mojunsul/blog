package io.github.mojunsul.blog.fixture;

import java.time.Instant;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.post.entity.Post;

public final class PostFixture {

	private PostFixture() {}

	public static Post draft(String slug) {
		return Post.builder().title("제목").slug(slug).content("본문").build();
	}

	public static Post published(String slug, Instant publishedAt) {
		return published(slug, publishedAt, null);
	}

	public static Post published(String slug, Instant publishedAt, Category category) {
		Post post = Post.builder().title("제목").slug(slug).content("본문").category(category).build();
		post.publish(publishedAt);
		return post;
	}
}
