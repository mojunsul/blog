package io.github.mojunsul.blog.fixture;

import java.time.Instant;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.post.entity.Post;

public final class PostFixture {

	private PostFixture() {}

	public static Post draft(String slug) {
		return draft(slug, null);
	}

	public static Post draft(String slug, Category category) {
		return Post.builder().title("제목").slug(slug).content("본문").category(category).build();
	}

	public static Post draft(String slug, String title, String content, Category category) {
		return Post.builder().title(title).slug(slug).content(content).category(category).build();
	}

	public static Post published(String slug, String title, String content, Category category) {
		Post post = draft(slug, title, content, category);
		post.publish(Instant.parse("2026-10-13T01:00:00Z"));
		return post;
	}

	public static Post published(String slug, Instant publishedAt) {
		return published(slug, publishedAt, null);
	}

	public static Post published(String slug, Instant publishedAt, Category category) {
		Post post = draft(slug, category);
		post.publish(publishedAt);
		return post;
	}
}
