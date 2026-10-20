package io.github.mojunsul.blog.domain.category.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.mojunsul.blog.domain.category.dto.CategoryResponse;
import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.post.entity.Post;
import io.github.mojunsul.blog.domain.post.repository.PostRepository;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.fixture.PostFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;

class CategoryRepositoryTest extends RepositoryTestSupport {

	private static final Instant PUBLISHED_AT = Instant.parse("2026-10-14T01:00:00Z");

	@Autowired
	CategoryRepository categoryRepository;

	@Autowired
	PostRepository postRepository;

	@Autowired
	EntityManager em;

	@Test
	void 글_개수는_PUBLISHED_글만_센다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));
		postRepository.save(PostFixture.published("published-1", PUBLISHED_AT, category));
		postRepository.save(PostFixture.published("published-2", PUBLISHED_AT, category));
		postRepository.save(PostFixture.draft("draft", category));

		// when
		List<CategoryResponse> result = categoryRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).extracting(CategoryResponse::slug, CategoryResponse::postCount)
			.containsExactly(tuple("backend", 2L));
	}

	@Test
	void DRAFT만_있거나_글이_없는_카테고리도_0으로_포함된다() {
		// given
		Category onlyDraft = categoryRepository.save(CategoryFixture.category("only-draft"));
		categoryRepository.save(CategoryFixture.category("empty"));
		postRepository.save(PostFixture.draft("draft", onlyDraft));

		// when
		List<CategoryResponse> result = categoryRepository.findAllWithPublishedPostCount();

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
		List<CategoryResponse> result = categoryRepository.findAllWithPublishedPostCount();

		// then
		assertThat(result).extracting(CategoryResponse::slug)
			.containsExactly("alpha", "beta-first", "beta-second");
	}

	@Test
	void 벌크_삭제는_지운_행_수를_돌려준다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));

		// when & then
		assertThat(categoryRepository.deleteByIdReturningCount(category.getId())).isEqualTo(1);
		assertThat(categoryRepository.deleteByIdReturningCount(category.getId())).isZero();
	}

	@Test
	void 삭제하면_글의_category_id가_NULL이_되고_글은_남는다() {
		// given
		Category category = categoryRepository.save(CategoryFixture.category("backend"));
		Post post = postRepository.saveAndFlush(PostFixture.draft("post", category));

		// when
		categoryRepository.deleteByIdReturningCount(category.getId());
		em.clear();

		// then
		assertThat(postRepository.findById(post.getId())).get().extracting(Post::getCategory).isNull();
	}

	@Test
	void slug가_중복이면_INSERT에서_DataIntegrityViolationException이다() {
		// given
		categoryRepository.saveAndFlush(CategoryFixture.category("backend"));

		// when & then
		assertThatThrownBy(() -> categoryRepository.saveAndFlush(CategoryFixture.category("다른 이름", "backend")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 수정이_다른_카테고리의_slug면_flush에서_DataIntegrityViolationException이다() {
		// given
		categoryRepository.saveAndFlush(CategoryFixture.category("backend"));
		Category other = categoryRepository.saveAndFlush(CategoryFixture.category("infra"));

		// when
		other.update("Infra", "backend");

		// then
		assertThatThrownBy(() -> categoryRepository.flush()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 수정이_자기_slug_그대로면_flush에서_예외가_없다() {
		// given
		Category category = categoryRepository.saveAndFlush(CategoryFixture.category("backend"));

		// when
		category.update("바뀐 이름", "backend");

		// then
		assertThatCode(() -> categoryRepository.flush()).doesNotThrowAnyException();
	}

	@Test
	void 읽은_카테고리를_벌크_삭제한_뒤_수정해_flush하면_ObjectOptimisticLockingFailureException이다() {
		// given
		Category category = categoryRepository.saveAndFlush(CategoryFixture.category("backend"));
		categoryRepository.deleteByIdReturningCount(category.getId());

		// when
		category.update("Backend", "backend-2");

		// then
		assertThatThrownBy(() -> categoryRepository.flush())
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	@Test
	void name에_NUL이_있으면_INSERT가_DataIntegrityViolationException이다() {
		// when & then
		assertThatThrownBy(() -> categoryRepository.saveAndFlush(CategoryFixture.category("a\u0000b", "nul")))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
