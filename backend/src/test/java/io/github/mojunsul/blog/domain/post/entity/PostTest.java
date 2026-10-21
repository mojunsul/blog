package io.github.mojunsul.blog.domain.post.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.fixture.CategoryFixture;
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

	@Test
	void DRAFT로_돌려도_최초_발행_시각은_유지된다() {
		// given
		Instant first = Instant.parse("2026-10-13T01:00:00Z");
		Post post = PostFixture.published("slug", first);

		// when
		post.toDraft();

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
		assertThat(post.getPublishedAt()).isEqualTo(first);
	}

	@Test
	void DRAFT로_돌렸다_다시_발행해도_최초_발행_시각은_바뀌지_않는다() {
		// given
		Instant first = Instant.parse("2026-10-13T01:00:00Z");
		Post post = PostFixture.published("slug", first);
		post.toDraft();

		// when
		post.publish(Instant.parse("2026-10-14T01:00:00Z"));

		// then
		assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(post.getPublishedAt()).isEqualTo(first);
	}

	@Test
	void 수정하면_본문_필드를_모두_바꾸고_상태와_발행_시각은_건드리지_않는다() {
		// given
		Category category = CategoryFixture.category("backend");
		Post post = PostFixture.draft("old");

		// when
		post.update("새 제목", "new", "요약", "새 본문", "https://example.com/a.png", category);

		// then
		assertThat(post.getTitle()).isEqualTo("새 제목");
		assertThat(post.getSlug()).isEqualTo("new");
		assertThat(post.getSummary()).isEqualTo("요약");
		assertThat(post.getContent()).isEqualTo("새 본문");
		assertThat(post.getThumbnailUrl()).isEqualTo("https://example.com/a.png");
		assertThat(post.getCategory()).isSameAs(category);
		assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
		assertThat(post.getPublishedAt()).isNull();
	}
}
