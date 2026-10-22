package io.github.mojunsul.blog.domain.post.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import io.github.mojunsul.blog.domain.category.entity.Category;
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
		Page<Post> page = postRepository.findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus.PUBLISHED,
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
		Page<Post> page = postRepository.findAllByStatusOrderByPublishedAtDescIdDesc(PostStatus.PUBLISHED,
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
		Optional<Post> found = postRepository.findBySlugAndStatus("published", PostStatus.PUBLISHED);

		// then
		assertThat(found).get().extracting(Post::getSlug).isEqualTo("published");
	}

	@Test
	void slug와_PUBLISHED_상태로_조회하면_카테고리가_함께_로딩된다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));
		postRepository.save(PostFixture.published("published", Instant.parse("2026-10-13T01:00:00Z"), category));
		em.flush();
		em.clear();

		// when
		Post found = postRepository.findBySlugAndStatus("published", PostStatus.PUBLISHED).orElseThrow();

		// then
		assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(found.getCategory())).isTrue();
	}

	@Test
	void slug와_PUBLISHED_상태로_조회하면_DRAFT_글은_나오지_않는다() {
		// given
		postRepository.save(PostFixture.draft("draft"));

		// when
		Optional<Post> found = postRepository.findBySlugAndStatus("draft", PostStatus.PUBLISHED);

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

	@Test
	void 관리자_목록은_상태와_관계없이_id_내림차순으로_조회한다() {
		// given
		postRepository.save(PostFixture.published("first", Instant.parse("2026-10-13T01:00:00Z")));
		postRepository.save(PostFixture.draft("second"));
		postRepository.save(PostFixture.published("third", Instant.parse("2026-10-12T01:00:00Z")));

		// when
		Page<Post> page = postRepository.findAllByOrderByIdDesc(PageRequest.of(0, 10));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("third", "second", "first");
	}

	@Test
	void 관리자_목록을_상태로_조회하면_그_상태의_글만_id_내림차순으로_나온다() {
		// given
		postRepository.save(PostFixture.draft("draft-first"));
		postRepository.save(PostFixture.published("published", Instant.parse("2026-10-13T01:00:00Z")));
		postRepository.save(PostFixture.draft("draft-second"));

		// when
		Page<Post> page = postRepository.findAllByStatusOrderByIdDesc(PostStatus.DRAFT, PageRequest.of(0, 10));

		// then
		assertThat(page.getContent()).extracting(Post::getSlug).containsExactly("draft-second", "draft-first");
	}

	@Test
	void 관리자_목록을_조회하면_카테고리가_함께_로딩된다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));
		postRepository.save(PostFixture.draft("draft", category));
		em.flush();
		em.clear();

		// when
		Post found = postRepository.findAllByOrderByIdDesc(PageRequest.of(0, 10)).getContent().get(0);

		// then
		assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(found.getCategory())).isTrue();
	}

	@Test
	void id로_조회하면_DRAFT와_PUBLISHED_글이_모두_카테고리와_함께_나온다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));
		Long draftId = postRepository.save(PostFixture.draft("draft", category)).getId();
		Long publishedId = postRepository
			.save(PostFixture.published("published", Instant.parse("2026-10-13T01:00:00Z"), category))
			.getId();
		em.flush();
		em.clear();

		// when
		Post draft = postRepository.findWithCategoryById(draftId).orElseThrow();
		Post published = postRepository.findWithCategoryById(publishedId).orElseThrow();

		// then
		assertThat(draft.getStatus()).isEqualTo(PostStatus.DRAFT);
		assertThat(published.getStatus()).isEqualTo(PostStatus.PUBLISHED);
		assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(draft.getCategory())).isTrue();
		assertThat(em.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(published.getCategory())).isTrue();
	}

	@Test
	void 삭제하면_지운_행_수를_돌려준다() {
		// given
		Post post = postRepository.save(PostFixture.draft("post"));

		// when & then
		assertThat(postRepository.deleteByIdReturningCount(post.getId())).isEqualTo(1);
		assertThat(postRepository.deleteByIdReturningCount(post.getId())).isZero();
	}
}
