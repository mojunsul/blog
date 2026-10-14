package io.github.mojunsul.blog.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;

class PostRepositoryTest extends RepositoryTestSupport {

	@Autowired
	PostRepository postRepository;

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	EntityManager em;

	@Test
	void 상태로_조회하면_PUBLISHED_글만_나온다() {
		// given
		postRepository.save(PostFixture.published("published", Instant.parse("2026-10-13T01:00:00Z")));
		postRepository.save(PostFixture.draft("draft"));

		// when
		var page = postRepository.findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus.PUBLISHED,
			PageRequest.of(0, 10));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("published");
	}

	@Test
	void 발행_시각_내림차순으로_조회하고_같으면_id_내림차순이다() {
		// given
		Instant same = Instant.parse("2026-10-13T01:00:00Z");
		postRepository.save(PostFixture.published("same-first", same));
		postRepository.save(PostFixture.published("newest", same.plusSeconds(60)));
		postRepository.save(PostFixture.published("same-second", same));

		// when
		var page = postRepository.findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus.PUBLISHED,
			PageRequest.of(0, 10));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug)
			.containsExactly("newest", "same-second", "same-first");
	}

	@Test
	void slug와_PUBLISHED_상태로_조회하면_PUBLISHED_글이_나온다() {
		// given
		postRepository.save(PostFixture.published("published", Instant.parse("2026-10-13T01:00:00Z")));

		// when
		var found = postRepository.findBySlugAndStatus("published", PostStatus.PUBLISHED);

		// then
		assertThat(found).get().extracting(Post::getSlug).isEqualTo("published");
	}

	@Test
	void slug와_PUBLISHED_상태로_조회하면_카테고리가_함께_로딩된다() {
		// given
		var category = categoryRepository.save(CategoryFixture.category("backend"));
		postRepository.save(PostFixture.published("published", Instant.parse("2026-10-13T01:00:00Z"), category));
		em.flush();
		em.clear();

		// when
		var found = postRepository.findBySlugAndStatus("published", PostStatus.PUBLISHED).orElseThrow();

		// then
		assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(found.getCategory())).isTrue();
	}

	@Test
	void slug와_PUBLISHED_상태로_조회하면_DRAFT_글은_나오지_않는다() {
		// given
		postRepository.save(PostFixture.draft("draft"));

		// when
		var found = postRepository.findBySlugAndStatus("draft", PostStatus.PUBLISHED);

		// then
		assertThat(found).isEmpty();
	}

	@Test
	void slug가_중복되면_저장할_수_없다() {
		// given
		postRepository.saveAndFlush(PostFixture.draft("same-slug"));

		// when & then
		assertThatThrownBy(() -> postRepository.saveAndFlush(PostFixture.draft("same-slug")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
