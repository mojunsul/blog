package io.github.mojunsul.blog.domain.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.mojunsul.blog.domain.category.dto.CategoryResponse;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;

class CategoryRepositoryTest extends RepositoryTestSupport {

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-14T01:00:00Z");

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	PostRepository postRepository;

	@Test
	void 글_개수는_PUBLISHED_글만_센다() {
		// given
		var category = categoryRepository.save(CategoryFixture.category("backend"));
		postRepository.save(PostFixture.published("published-1", PUBLISHED_AT, category));
		postRepository.save(PostFixture.published("published-2", PUBLISHED_AT, category));
		postRepository.save(PostFixture.draft("draft", category));

		// when
		var result = categoryRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).extracting(CategoryResponse::slug, CategoryResponse::postCount)
			.containsExactly(tuple("backend", 2L));
	}

	@Test
	void DRAFT만_있거나_글이_없는_카테고리도_0으로_포함된다() {
		// given
		var onlyDraft = categoryRepository.save(CategoryFixture.category("only-draft"));
		categoryRepository.save(CategoryFixture.category("empty"));
		postRepository.save(PostFixture.draft("draft", onlyDraft));

		// when
		var result = categoryRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).extracting(CategoryResponse::slug, CategoryResponse::postCount)
			.containsExactlyInAnyOrder(tuple("only-draft", 0L),
				tuple("empty", 0L));
	}

	@Test
	void name_오름차순으로_정렬하고_같으면_id_오름차순이다() {
		// given
		categoryRepository.save(CategoryFixture.category("beta", "beta-first"));
		categoryRepository.save(CategoryFixture.category("alpha", "alpha"));
		categoryRepository.save(CategoryFixture.category("beta", "beta-second"));

		// when
		var result = categoryRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).extracting(CategoryResponse::slug)
			.containsExactly("alpha", "beta-first", "beta-second");
	}
}
