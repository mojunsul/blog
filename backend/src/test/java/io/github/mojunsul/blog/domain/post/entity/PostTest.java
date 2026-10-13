package io.github.mojunsul.blog.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.mojunsul.blog.fixture.PostFixture;

class PostTest {

	@Test
	void 새_글은_DRAFT이고_발행_시각이_없다() {
		// when
		Post post = PostFixture.draft("slug");

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
		assertThat(post.getPublishedAt()).isNull();
	}

	@Test
	void 발행하면_PUBLISHED가_되고_발행_시각이_정해진다() {
		// given
		Post post = PostFixture.draft("slug");
		Instant now = Instant.parse("2026-10-13T01:00:00Z");

		// when
		post.publish(now);

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(post.getPublishedAt()).isEqualTo(now);
	}

	@Test
	void 다시_발행해도_최초_발행_시각은_바뀌지_않는다() {
		// given
		Instant first = Instant.parse("2026-10-13T01:00:00Z");
		Post post = PostFixture.published("slug", first);

		// when
		post.publish(Instant.parse("2026-10-14T01:00:00Z"));

		// then
		assertThat(post.getPublishedAt()).isEqualTo(first);
	}
}
